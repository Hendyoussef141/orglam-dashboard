@echo off
REM ===================================================================
REM  Sets up GitHub auto-deploy. Put this file in:
REM      D:\downloads\orglam dashboard
REM  then double-click it. It does steps 2 to 5 for you.
REM ===================================================================

cd /d "%~dp0"

REM Run from anywhere else and this would try to upload that whole folder - it ran once from a
REM Downloads folder and started adding everything in it. The app folder is the reliable marker;
REM the dashboard html lives in a subfolder here.
if not exist "orglam-app" (
  echo.
  echo  WRONG FOLDER
  echo.
  echo  This file must sit in the folder that contains "orglam-app"
  echo  - normally  D:\downloads\orglam dashboard
  echo.
  echo  It is currently in:
  echo      %~dp0
  echo.
  echo  Move it there and run it again. Nothing was changed.
  goto end
)

echo.
echo === Checking Git is installed ===
where git >nul 2>nul
if errorlevel 1 (
  echo Git was not found. Close this, install Git from https://git-scm.com/download/win
  echo and run this file again.
  goto end
)
git --version

echo.
echo === Writing .gitignore ===
> .gitignore echo node_modules/
>> .gitignore echo orglam-app/node_modules/
>> .gitignore echo orglam-app/android/.gradle/
>> .gitignore echo orglam-app/android/app/build/
>> .gitignore echo orglam-app/android/build/
>> .gitignore echo *.jks
>> .gitignore echo *.keystore
REM A Firebase admin key is a full server credential - it must never reach a repo.
>> .gitignore echo *adminsdk*.json
>> .gitignore echo .idea/
>> .gitignore echo *.log
echo done

echo.
echo === Writing .github\workflows\deploy.yml ===
if not exist ".github\workflows" mkdir ".github\workflows"
powershell -NoProfile -ExecutionPolicy Bypass -Command "[IO.File]::WriteAllBytes('.github\workflows\deploy.yml', [Convert]::FromBase64String('bmFtZTogRGVwbG95CgojIEV2ZXJ5IHB1c2ggdG8gbWFpbiBkZXBsb3lzIGV2ZXJ5dGhpbmc6IHRoZSB3b3JrZXIsIHRoZSBkYXNoYm9hcmQgYnVuZGxlZCBpbnRvIGl0LCBhbmQgYSBmcmVzaAojIHNpZ25lZCBBbmRyb2lkIGJ1aWxkIHB1Ymxpc2hlZCB0byBhbGwgdGhlIHBob25lcy4Kb246CiAgcHVzaDoKICAgIGJyYW5jaGVzOiBbbWFpbl0KICB3b3JrZmxvd19kaXNwYXRjaDoKCmpvYnM6CiAgd29ya2VyOgogICAgbmFtZTogQ2xvdWRmbGFyZSBXb3JrZXIKICAgIHJ1bnMtb246IHVidW50dS1sYXRlc3QKICAgIHN0ZXBzOgogICAgICAtIHVzZXM6IGFjdGlvbnMvY2hlY2tvdXRAdjQKCiAgICAgICMgd3JhbmdsZXIgZXhwZWN0cyB0aGUgZW50cnlwb2ludCBuYW1lZCBpbiB3cmFuZ2xlci50b21sIChtYWluID0gIndvcmtlci5qcyIpLCBidXQgdGhlIGZpbGUKICAgICAgIyBpcyBrZXB0IHVuZGVyIGl0cyByZWFkYWJsZSBuYW1lIGluIHRoZSByZXBvLgogICAgICAtIG5hbWU6IFN0YWdlIHRoZSB3b3JrZXIgZW50cnlwb2ludAogICAgICAgIHJ1bjogY3AgIk9yZ2xhbSBXb3JrZXIuanMiIHdvcmtlci5qcwoKICAgICAgLSBuYW1lOiBJbmplY3QgdGhlIEtWIG5hbWVzcGFjZSBpZAogICAgICAgIHJ1bjogfAogICAgICAgICAgc2VkIC1pICJzL1BBU1RFX1lPVVJfS1ZfTkFNRVNQQUNFX0lELyR7eyBzZWNyZXRzLktWX05BTUVTUEFDRV9JRCB9fS8iIHdyYW5nbGVyLnRvbWwKCiAgICAgIC0gdXNlczogY2xvdWRmbGFyZS93cmFuZ2xlci1hY3Rpb25AdjMKICAgICAgICB3aXRoOgogICAgICAgICAgYXBpVG9rZW46ICR7eyBzZWNyZXRzLkNMT1VERkxBUkVfQVBJX1RPS0VOIH19CiAgICAgICAgICBhY2NvdW50SWQ6ICR7eyBzZWNyZXRzLkNMT1VERkxBUkVfQUNDT1VOVF9JRCB9fQoKICBhbmRyb2lkOgogICAgbmFtZTogQW5kcm9pZCBidWlsZCArIHB1Ymxpc2gKICAgIHJ1bnMtb246IHVidW50dS1sYXRlc3QKICAgICMgTmV2ZXIgcHVibGlzaGVzIGFuIEFQSyB0aGUgd29ya2VyIGRlcGxveSBoYXNuJ3QgYWNjZXB0ZWQgZmlyc3QuCiAgICBuZWVkczogd29ya2VyCiAgICBzdGVwczoKICAgICAgLSB1c2VzOiBhY3Rpb25zL2NoZWNrb3V0QHY0CgogICAgICAtIHVzZXM6IGFjdGlvbnMvc2V0dXAtamF2YUB2NAogICAgICAgIHdpdGg6CiAgICAgICAgICBkaXN0cmlidXRpb246IHRlbXVyaW4KICAgICAgICAgIGphdmEtdmVyc2lvbjogJzIxJyAgICMgR3JhZGxlIDggbmVlZHMgMTctMjQ7IDI1IGZhaWxzIHdpdGggImNsYXNzIGZpbGUgbWFqb3IgdmVyc2lvbiA2OSIKCiAgICAgIC0gdXNlczogYWN0aW9ucy9zZXR1cC1ub2RlQHY0CiAgICAgICAgd2l0aDoKICAgICAgICAgIG5vZGUtdmVyc2lvbjogJzIwJwoKICAgICAgIyBUaGUgdmVyc2lvbiBudW1iZXIgbXVzdCBiZSBhIHJlYWwsIHJpc2luZyBpbnRlZ2VyIGNvbXBpbGVkIElOVE8gdGhlIGFwayAtIHRoZSBwaG9uZXMgY29tcGFyZQogICAgICAjIGl0IGFnYWluc3QgdGhlaXIgb3duIGluc3RhbGxlZCB2ZXJzaW9uQ29kZS4gVGhlIHJ1biBudW1iZXIgZ2l2ZXMgdGhhdCBmb3IgZnJlZSBhbmQgbmV2ZXIKICAgICAgIyByZXBlYXRzLgogICAgICAtIG5hbWU6IFNldCB0aGUgdmVyc2lvbiBudW1iZXIKICAgICAgICBpZDogdmVyCiAgICAgICAgcnVuOiB8CiAgICAgICAgICBWRVI9JCgoICR7eyBnaXRodWIucnVuX251bWJlciB9fSArIDEwMCApKQogICAgICAgICAgZWNobyAiY29kZT0kVkVSIiA+PiAkR0lUSFVCX09VVFBVVAogICAgICAgICAgc2VkIC1pIC1FICJzL3ZlcnNpb25Db2RlICtbMC05XSsvdmVyc2lvbkNvZGUgJFZFUi8iIG9yZ2xhbS1hcHAvYW5kcm9pZC9hcHAvYnVpbGQuZ3JhZGxlCiAgICAgICAgICBzZWQgLWkgLUUgInMvdmVyc2lvbk5hbWUgK1wiW15cIl0qXCIvdmVyc2lvbk5hbWUgXCIkVkVSLjBcIi8iIG9yZ2xhbS1hcHAvYW5kcm9pZC9hcHAvYnVpbGQuZ3JhZGxlCgogICAgICAtIG5hbWU6IENvcHkgdGhlIGRhc2hib2FyZCBpbnRvIHRoZSBhcHAKICAgICAgICBydW46IHwKICAgICAgICAgIGNwICJPcmdsYW0gRGFzaGJvYXJkLmh0bWwiIG9yZ2xhbS1hcHAvd3d3LwogICAgICAgICAgY3Agc3cuanMgbWFuaWZlc3QuanNvbiBvcmdsYW0tYXBwL3d3dy8gMj4vZGV2L251bGwgfHwgdHJ1ZQogICAgICAgICAgY2Qgb3JnbGFtLWFwcAogICAgICAgICAgbnBtIGNpIHx8IG5wbSBpbnN0YWxsCiAgICAgICAgICBucHggY2FwIGNvcHkgYW5kcm9pZAoKICAgICAgIyBUaGUgc2FtZSBrZXlzdG9yZSB0aGUgaW5zdGFsbGVkIGFwcCB3YXMgc2lnbmVkIHdpdGgsIHN0b3JlZCBhcyBhIGJhc2U2NCBzZWNyZXQgLSBhIGJ1aWxkCiAgICAgICMgc2lnbmVkIHdpdGggYSBkaWZmZXJlbnQga2V5IGNhbm5vdCBpbnN0YWxsIG92ZXIgd2hhdCBpcyBhbHJlYWR5IG9uIHBlb3BsZSdzIHBob25lcy4KICAgICAgLSBuYW1lOiBSZXN0b3JlIHRoZSBzaWduaW5nIGtleXN0b3JlCiAgICAgICAgcnVuOiB8CiAgICAgICAgICBlY2hvICIke3sgc2VjcmV0cy5BTkRST0lEX0tFWVNUT1JFX0JBU0U2NCB9fSIgfCBiYXNlNjQgLWQgPiBvcmdsYW0tYXBwL2FuZHJvaWQvYXBwL3JlbGVhc2UuamtzCgogICAgICAtIG5hbWU6IEJ1aWxkIHRoZSByZWxlYXNlIEFQSwogICAgICAgIHdvcmtpbmctZGlyZWN0b3J5OiBvcmdsYW0tYXBwL2FuZHJvaWQKICAgICAgICBlbnY6CiAgICAgICAgICBPUkdMQU1fU1RPUkVfUEFTU1dPUkQ6ICR7eyBzZWNyZXRzLkFORFJPSURfU1RPUkVfUEFTU1dPUkQgfX0KICAgICAgICAgIE9SR0xBTV9LRVlfQUxJQVM6ICR7eyBzZWNyZXRzLkFORFJPSURfS0VZX0FMSUFTIH19CiAgICAgICAgICBPUkdMQU1fS0VZX1BBU1NXT1JEOiAke3sgc2VjcmV0cy5BTkRST0lEX0tFWV9QQVNTV09SRCB9fQogICAgICAgIHJ1bjogLi9ncmFkbGV3IGFzc2VtYmxlUmVsZWFzZSAtLW5vLWRhZW1vbgoKICAgICAgLSBuYW1lOiBQdWJsaXNoIHRvIGV2ZXJ5IHBob25lCiAgICAgICAgcnVuOiB8CiAgICAgICAgICBBUEs9JChmaW5kIG9yZ2xhbS1hcHAvYW5kcm9pZC9hcHAvYnVpbGQvb3V0cHV0cy9hcGsvcmVsZWFzZSAtbmFtZSAiKi5hcGsiIHwgaGVhZCAtMSkKICAgICAgICAgIGVjaG8gIlB1Ymxpc2hpbmcgJEFQSyBhcyB2ZXJzaW9uICR7eyBzdGVwcy52ZXIub3V0cHV0cy5jb2RlIH19IgogICAgICAgICAgY3VybCAtZiAtWCBQVVQgLVQgIiRBUEsiIFwKICAgICAgICAgICAgLUggIkNvbnRlbnQtVHlwZTogYXBwbGljYXRpb24vdm5kLmFuZHJvaWQucGFja2FnZS1hcmNoaXZlIiBcCiAgICAgICAgICAgICJodHRwczovL2Rhd24ta2luZy02Y2MzLm9yZ2xhbS1zZXJ2aWNlLndvcmtlcnMuZGV2L2FwcC11cGxvYWQ/dmVyc2lvbkNvZGU9JHt7IHN0ZXBzLnZlci5vdXRwdXRzLmNvZGUgfX0mYnk9Z2l0aHViIgo='))"
if errorlevel 1 goto failed
echo done

echo.
echo === Setting up the repository ===
if not exist ".git" git init
git branch -M main
REM Set BEFORE anything can commit. Git refuses to commit without an identity ("Author identity
REM unknown"), and it is set globally so a later re-init of this repo cannot lose it again.
git config --global user.name "Hendyoussef141"
git config --global user.email "Hendyoussef141@users.noreply.github.com"
git config user.name "Hendyoussef141"
git config user.email "Hendyoussef141@users.noreply.github.com"
git remote remove origin 2>nul
git remote add origin https://github.com/Hendyoussef141/orglam-dashboard.git

echo.
echo === Sending everything to GitHub ===
echo (A browser window may open asking you to sign in to GitHub - approve it.)
git add -A
git commit -m "Orglam dashboard, worker and auto-deploy"
REM A failed commit must stop here: pushing next would only report the confusing "src refspec main
REM does not match any", which hides the real reason.
git rev-parse --verify HEAD >nul 2>&1
if errorlevel 1 (
  echo.
  echo  The commit did not happen - see the message just above.
  echo  Nothing was pushed.
  goto end
)
git push -u origin main
if errorlevel 1 goto pushfailed

echo.
echo ==========================================
echo  Done. Everything is on GitHub.
echo.
echo  Next: add the secrets, as listed in
echo  android-app-guide\github-auto-deploy.md
echo ==========================================
goto end

:pushfailed
echo.
echo The push failed. Most common reasons:
echo   - You cancelled the GitHub sign-in window.
echo   - The repo already has commits: run  git pull --rebase origin main  then this file again.
goto end

:failed
echo.
echo Could not write the workflow file.

:end
echo.
pause
