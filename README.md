# EduCaster Live Desktop

This application allows teachers to stream live classes via Mux and manage their video assets using Cloudflare R2 storage.

## Features
- **Live Streaming**: Create, preview, and manage Mux live streams.
- **Video Storage (New)**: Upload and manage files in Cloudflare R2 Buckets.
- **Robust Uploads**: Support for cancelling uploads safely with progress tracking.
- **Large File Support**: Supports uploading files up to 5TB via multipart uploads.
- **Secure Configuration**: Encrypted storage for API keys and secrets.
- **Professional UI**: Modern, responsive interface with detailed help guides.

## Compilation Guide

This guide explains how to compile the application and package it into a Windows Executable (`.exe`) using Java's `jpackage` tool.

## Prerequisites

1.  **Java Development Kit (JDK) 17+**: Ensure you have JDK 17 or later installed.
    *   Verify by running: `java -version`
2.  **Maven**: Ensure Maven is installed and added to your PATH.
    *   Verify by running: `mvn -version`
3.  **WiX Toolset (Optional but Recommended)**: Required by `jpackage` to build `.msi` or `.exe` installers.
    *   Download from: [https://wixtoolset.org/](https://wixtoolset.org/)

## Step 1: Build the Fat JAR

First, we need to compile the project and create a "shaded" JAR file that includes all dependencies.

Open your terminal in the project root (`d:\Major Project\BR31Demo\Educater`) and run:

```powershell
mvn clean package
```

This will create a JAR file in the `target` directory, typically named `educater-live-mux-6.0.0.jar`.

## Step 2: Create the Executable (.exe)

We will use the `jpackage` tool (included in JDK 14+) to bundle the JAR into a standalone executable.

Run the following command in your terminal. 

**Note**: You may need to adjust the path to the `--input` directory or `--main-jar` if they differ.

```powershell
jpackage --type app-image --input target --main-jar educater-live-mux-6.0.0.jar --main-class com.educater.ui.MainApp --name EduCasterLive --icon src/main/resources/images/educaster.ico --dest dist --vendor "BR31 Technologies" --app-version 6.0.0
```

### Explanation of Flags:
*   `--type app-image`: Creates a directory containing the executable and a bundled JRE (no installer). Use `--type exe` to create an installer (requires WiX).
*   `--input target`: The directory containing your JAR file.
*   `--main-jar ...`: The name of your built JAR file.
*   `--main-class ...`: The main entry point of the application.
*   `--name "EduCasterLive"`: The name of the output executable.
*   `--icon ...`: Path to the `.ico` or `.png` file to use as the application icon.
*   `--dest dist`: The output directory where the app will be placed.
*   `--win-console`: (Optional) Keeps the console window open. Remove this flag for a production build if you don't want the terminal to show.

## Step 3: Run the Application

Navigate to the `dist/EduCasterLive` directory and double-click `EduCasterLive.exe`.

---

## Configuration

### Cloudflare R2 Setup (AWS S3 Compatible)
This application uses Cloudflare R2 storage, which provides an S3-compatible API. You will need to generate API keys from your Cloudflare dashboard.

#### 1. Create R2 Bucket
1.  Log in to the **Cloudflare Dashboard**.
2.  Navigate to **R2** from the sidebar.
3.  Click **Create Bucket**.
4.  Give it a name (e.g., `educater-assets`) and click **Create Bucket**.

#### 2. Get Account ID
1.  On the main **R2 Overview** page, look for **Account ID** on the right sidebar.
2.  Copy this ID. You will need it for the application settings.

#### 3. Generate API Keys (S3 Credentials)
1.  On the R2 Overview page, click **Manage R2 API Tokens** (top right).
2.  Click **Create API Token**.
3.  **Token name**: Enter a name (e.g., `EduCaster Desktop`).
4.  **Permissions**: Select **Object Read & Write** (Important: Read-only will fail uploads).
5.  **TTL**: Set to "Forever" or as desired.
6.  Click **Create API Token**.
7.  **IMPORTANT**: Copy the **Access Key ID** and **Secret Access Key** immediately. You cannot see the secret key again.

#### 4. Configure Public Access (Optional but Recommended)
1.  Go to your Bucket settings.
2.  Under **Public Access**, you can either:
    *   **Connect Custom Domain**: If you have a domain on Cloudflare (e.g., `cdn.myschool.com`).
    *   **Allow R2.dev subdomain**: Toggle this to allow public access via a `pub-xxx.r2.dev` link (Note: This may require authentication or have limits).
3.  Copy this URL (e.g., `https://cdn.myschool.com`).

#### 5. Configure EduCaster Application
1.  Open EduCaster Live Desktop.
2.  Click **Admin Settings** (Log in as Admin if needed).
3.  Go to the **Cloudflare R2 Storage** section.
4.  Enter the values you copied:
    *   **Account ID**: From step 2.
    *   **Access Key ID**: From step 3.
    *   **Secret Access Key**: From step 3.
    *   **Bucket Name**: From step 1.
    *   **Public URL**: From step 4 (e.g., `https://cdn.myschool.com`).
5.  Click **Save Settings**.

## Troubleshooting

*   **"jpackage not found"**: Ensure your `%JAVA_HOME%\bin` is in your system PATH.
*   **Missing Icon**: `jpackage` on Windows prefers `.ico` files. If `.png` fails, convert `educaster.png` to `.ico` using an online converter and update the `--icon` path.
*   **WiX Error**: If you try to use `--type exe` or `--type msi`, you must install the WiX Toolset.
