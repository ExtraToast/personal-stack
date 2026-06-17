@echo off
REM ============================================================================
REM WINDOWS-HARDENING.cmd
REM RX 7900 XTX dual-boot corruption prevention - Windows hardening helper
REM
REM REQUIREMENTS:
REM   Run this script as Administrator (right-click -> Run as administrator).
REM
REM PURPOSE:
REM   Disables Windows hibernation (and therefore Fast Startup) and optionally
REM   excludes AMD display drivers from Windows Update. These two changes prevent
REM   Windows from restoring a stale kernel/driver image against a GPU that Linux
REM   left in a dirty power-persistent state after a warm reboot.
REM
REM   This script does NOT automate DDU. Run DDU manually in Safe Mode as
REM   described in Phase 0 of REMEDIATION-RUNBOOK.md.
REM
REM ROLLBACK COMMANDS (run elevated, after validation is complete):
REM   powercfg /h on
REM   reg delete "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate /f
REM   gpupdate /force
REM ============================================================================

echo.
echo ============================================================
echo  RX 7900 XTX Windows hardening
echo  Requires: elevated (Administrator) prompt
echo ============================================================
echo.

REM ----------------------------------------------------------------------------
REM Step 1: Disable hibernation (also disables Fast Startup)
REM ----------------------------------------------------------------------------
echo [1/4] Disabling hibernation (disables Fast Startup)...
powercfg /h off
if %ERRORLEVEL% neq 0 (
    echo ERROR: powercfg /h off failed. Are you running as Administrator?
    goto :error
)
echo       OK

REM ----------------------------------------------------------------------------
REM Step 2: Report available sleep/wake states to confirm hibernation is gone
REM ----------------------------------------------------------------------------
echo.
echo [2/4] Checking available power states (hibernation should be absent)...
powercfg /a
echo.

REM ----------------------------------------------------------------------------
REM Step 3: Set HiberbootEnabled registry key to 0 (belt-and-suspenders)
REM ----------------------------------------------------------------------------
echo [3/4] Setting HiberbootEnabled=0 in registry...
reg add "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Power" /v HiberbootEnabled /t REG_DWORD /d 0 /f
if %ERRORLEVEL% neq 0 (
    echo ERROR: registry write failed.
    goto :error
)
echo       OK

REM ----------------------------------------------------------------------------
REM Step 4: Optional - exclude display drivers from Windows Update
REM
REM Uncomment the block below if you want to prevent Windows Update from
REM replacing the manually installed AMD driver during the validation period.
REM This requires Group Policy support (Windows Pro, Enterprise, or Education).
REM ----------------------------------------------------------------------------

REM echo [4/4] Excluding drivers from Windows Update (optional)...
REM reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate /t REG_DWORD /d 1 /f
REM if %ERRORLEVEL% neq 0 (
REM     echo ERROR: ExcludeWUDriversInQualityUpdate registry write failed.
REM     goto :error
REM )
REM gpupdate /force
REM echo       OK

echo [4/4] Driver exclusion step is commented out. Uncomment in the script to enable.

REM ----------------------------------------------------------------------------
REM Verification output
REM ----------------------------------------------------------------------------
echo.
echo ============================================================
echo  Verification
echo ============================================================
echo.
echo HiberbootEnabled registry value (should be 0x0):
reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Power" /v HiberbootEnabled
echo.
echo ExcludeWUDriversInQualityUpdate (present only if step 4 was enabled):
reg query "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate 2>nul || echo       (not set)
echo.
echo ============================================================
echo  IMPORTANT: confirm in GUI
echo    Control Panel -> Power Options
echo    -> Choose what the power buttons do
echo    -> Change settings that are currently unavailable
echo    -> "Turn on fast startup" must be unchecked or absent
echo ============================================================
echo.
echo Done. Use Restart (not Shut down) when leaving Windows for another OS.
goto :end

:error
echo.
echo FAILED. Re-run this script as Administrator.
exit /b 1

:end
exit /b 0
