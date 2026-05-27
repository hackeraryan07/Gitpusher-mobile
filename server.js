const express = require('express');
const path = require('path');
const fs = require('fs');

const app = express();
const PORT = 3000;

// Serve public directory
app.use(express.static(path.join(__dirname, 'public')));

// Helper to find the APK
function getApkPath(type) {
  const paths = type === 'release' 
    ? [
        path.join(__dirname, 'app/build/outputs/apk/release/app-release.apk'),
        path.join(__dirname, 'app/build/outputs/apk/release/app-release-unsigned.apk'),
      ]
    : [
        path.join(__dirname, 'app/build/outputs/apk/debug/app-debug.apk'),
        path.join(__dirname, '.build-outputs/app-debug.apk'),
      ];

  for (const p of paths) {
    if (fs.existsSync(p)) {
      return p;
    }
  }
  return null;
}

// Endpoint to check APK status
app.get('/api/apk-status', (req, res) => {
  const releasePath = getApkPath('release');
  const debugPath = getApkPath('debug');
  res.json({
    releaseExists: !!releasePath,
    debugExists: !!debugPath
  });
});

// Endpoint to download Release APK
app.get('/download/release', (req, res) => {
  const apk = getApkPath('release');
  if (apk) {
    res.download(apk, 'GitHub_Repo_Explorer_release.apk');
  } else {
    // Fallback to debug if release is not built yet
    const debugApk = getApkPath('debug');
    if (debugApk) {
      res.download(debugApk, 'GitHub_Repo_Explorer_debug.apk');
    } else {
      res.status(404).send('APK files are currently building. Please refresh in a few moments.');
    }
  }
});

// Endpoint to download Debug APK
app.get('/download/debug', (req, res) => {
  const apk = getApkPath('debug');
  if (apk) {
    res.download(apk, 'GitHub_Repo_Explorer_debug.apk');
  } else {
    res.status(404).send('Debug APK file not found. Ensure the build has completed.');
  }
});

// Fallback all other routes to index.html
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Web server running successfully on http://0.0.0.0:${PORT}`);
});
