#!/bin/bash
#
# File: log.sh
#
# Created: Friday, 31 May 2013
#

adb logcat | awk '/AndroidRuntime|Tasks|DBHelper|DEBUG/ && !/ActivityManager|Multiwindow|MultiWindowManagerService/'

exit 0

