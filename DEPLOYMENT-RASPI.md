# Deployment Guide: Raspberry Pi CI/CD (GitHub Actions + Tailscale + SSH)

This guide explains how to set up automatic deployment for the Python monitoring scripts under the `raspberry-pi/` folder to a physical Raspberry Pi using GitHub Actions, Tailscale, and SSH.

```mermaid
graph LR
    Developer[Developer] -- Git Push --> GitHub[GitHub Repository]
    GitHub -- "GitHub Action (Runs Workflow)" --> runner[Ephemeral Runner]
    runner -- "Joins Private Network" --> TS[Tailscale Network]
    TS -- "Deploy (Rsync over SSH)" --> Pi[Raspberry Pi]
```

## Deployment Strategy
For this project, Raspberry Pi runs the Python script periodically via a `cron` job (e.g. every minute), writing updated Python scripts directly into the active folder poses risks:
1. **Single-file corruption**: Opening or importing a file while it is being partially written.
2. **Multi-file inconsistency**: Loading a new `config.py` but an old `waterlevel.py` because the file transfers occurred sequentially.

**Collision Avoidance & Zero-Downtime Swaps**

To eliminate these risks, we use an **atomic symlink swap pattern** (commonly referred to as blue-green deployments at the directory level):
1. The GitHub Action uploads all files to a temporary `staging/` directory using `rsync`.
2. Once the transfer completes, the files are copied into a unique, timestamped release directory (e.g., `releases/20260620_094000/`).
3. We update a symlink (`current`) to point to the new directory. The OS swaps the symlink target **atomically**, ensuring any running cron job or starting service sees either 100% of the old version or 100% of the new version.

---

## Step 1: Set Up Tailscale (For Secure Network Connectivity)
Instead of forwarding ports on your home router (which exposes your network to the public internet), Tailscale allows the GitHub runner and your Raspberry Pi to securely communicate over a private mesh network.

1. **Install Tailscale on your Raspberry Pi**:

    Add the Tailscale Repository and GPG Key for Trixie
   ```bash
    curl -fsSL https://pkgs.tailscale.com/stable/debian/trixie.noarmor.gpg | sudo tee /usr/share/keyrings/tailscale-archive-keyring.gpg >/dev/null

    curl -fsSL https://pkgs.tailscale.com/stable/debian/trixie.tailscale-keyring.list | sudo tee /etc/apt/sources.list.d/tailscale.list
   ```

  Install and Start Tailscale
   ```bash
    sudo apt-get update
    sudo apt-get install tailscale
    sudo tailscale up
   ```
  When the Tailscale service starts, it asks to authenticate. Open the shown URL and authenticate using your Tailscale account. The Raspi is added to the device list.  

2. **Create a Tailscale OAuth Client** (Recommended / Set-and-Forget):
   - Log into your **Tailscale Admin Console**, go to **Settings**, and select **Trust credentials** (previously named OAuth).
   - Click **Generate OAuth client...**.
   - Under **Scopes**, look for **Auth keys** and grant it **Write** access (`auth_keys:write`). This allows the credential to dynamically generate keys to authenticate the ephemeral GitHub Action runner.
   - Under **Tags**, select the tags that this client is allowed to apply to the runner device (for example, `tag:github-ci`). 
     *(Note: If you don't have tags defined yet, you can create them in your tailnet Access Control Lists (ACLs) first).*
   - Click **Generate client** and copy both the **Client ID** and the **Client Secret**.

---

## Step 2: Configure SSH Access on the Pi
Generate a dedicated SSH key pair for the deployment pipeline to ensure the runner has secure, automated access.

1. **On your local machine (or the Pi), generate the key pair**:
   ```bash
   ssh-keygen -t ed25519 -f id_github_deploy -C "github-actions-deploy"
   ```
   *(Leave the passphrase empty).*
2. **Install the Public Key on the Pi**:
   Copy the contents of `id_github_deploy.pub` and append it to the Pi's authorized keys:
   ```bash
   mkdir -p ~/.ssh
   echo "PASTE_PUBLIC_KEY_CONTENT_HERE" >> ~/.ssh/authorized_keys
   chmod 600 ~/.ssh/authorized_keys
   chmod 700 ~/.ssh
   ```
3. Get your Raspberry Pi's **Tailscale IP address** by running `tailscale ip -4` on the Pi (e.g., `100.115.12.34`).

---

## Step 3: Add Secrets to GitHub
In your GitHub repository, navigate to **Settings** -> **Secrets and variables** -> **Actions** and add the following **Repository Secrets**:

| Secret Name | Value |
| :--- | :--- |
| `TS_OAUTH_CLIENT_ID` | The Tailscale OAuth Client ID generated in Step 1. |
| `TS_OAUTH_SECRET` | The Tailscale OAuth Client Secret generated in Step 1. |
| `SSH_PRIVATE_KEY` | The complete text content of the private key file `id_github_deploy`. |
| `PI_IP` | The Pi's Tailscale IP address (e.g., `100.115.12.34`). |
| `PI_USER` | The SSH username on your Pi (e.g., `pi`). |

---

## Step 4:
Prepare the target folder on the Raspberry Pi. This folder will be the upload target of the script files.

```bash
mikdir -p /home/pisonic/waterlevel-monitor/staging
```
---

## Step 5: Create the GitHub Actions Workflow
Create a new file in your project at `.github/workflows/deploy-pi.yml` with the following content:

```yaml
name: Deploy Python Scripts to Raspberry Pi

on:
  push:
    branches:
      - main
    paths:
      - 'raspberry-pi/**' # Only run when code in the raspberry-pi folder changes
  workflow_dispatch: # Allows manual trigger from the GitHub Actions tab without making a commit



jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      # 1. Check out the repository
      - name: Checkout Code
        uses: actions/checkout@v5


      # 2. Connect to your private Tailscale network
      - name: Connect to Tailscale
        uses: tailscale/github-action@v2
        with:
          oauth-client-id: ${{ secrets.TS_OAUTH_CLIENT_ID }}
          oauth-secret: ${{ secrets.TS_OAUTH_SECRET }}
          tags: tag:github-ci

      # 3. Deploy the files to a staging directory via rsync
      - name: Deploy files to Staging
        uses: easingthemes/ssh-deploy@main
        with:
          SSH_PRIVATE_KEY: ${{ secrets.SSH_PRIVATE_KEY }}
          ARGS: "-rlgoDzvO --delete" # Rsync flags
          SOURCE: "raspberry-pi/"
          REMOTE_HOST: ${{ secrets.PI_IP }}
          REMOTE_USER: ${{ secrets.PI_USER }}
          TARGET: "/home/${{ secrets.PI_USER }}/waterlevel-monitor/staging"

          # Move staging to a timestamped folder and swap the symlink atomically
          SCRIPT_AFTER: |
            TIMESTAMP=$(date +%Y%m%d_%H%M%S)
            RELEASE_DIR="/home/${{ secrets.PI_USER }}/waterlevel-monitor/releases/$TIMESTAMP"
            CURRENT_LINK="/home/${{ secrets.PI_USER }}/waterlevel-monitor/current"

            echo "Creating release: $RELEASE_DIR"
            mkdir -p "$RELEASE_DIR"
            cp -r /home/${{ secrets.PI_USER }}/waterlevel-monitor/staging/* "$RELEASE_DIR/"

            echo "Swapping symlink atomically..."
            ln -sfn "$RELEASE_DIR" "$CURRENT_LINK"

            echo "Cleaning up old releases (keeping last 5)..."
            cd /home/${{ secrets.PI_USER }}/waterlevel-monitor/releases && ls -t | tail -n +6 | xargs rm -rf
```

### How to Test the Workflow
Since we added the `workflow_dispatch` trigger, you can test the deployment pipeline without making dummy commits to the repository:
1. Push the workflow file (`.github/workflows/deploy-pi.yml`) to your GitHub repository.
2. Go to your repository on **GitHub.com** and click the **Actions** tab.
3. Select **Deploy Python Scripts to Raspberry Pi** from the list of workflows on the left.
4. Click the **Run workflow** dropdown on the right side of the screen.
5. Select the branch you want to run the workflow from (e.g., `main`) and click the green **Run workflow** button.

---

## Step 6: Configure your Cron or systemd Execution
Always point your execution target to the `current` symlink.
```cronexp
* * * * * /bin/bash /home/pi/waterlevel-monitor/current/run_waterlevel.sh
```
