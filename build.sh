#!/bin/bash
#
# File: build.sh
#
# Created: Saturday,  1 June 2013
#

if ant release && ant installr; then
# if ant debug && ant installd; then
    adb shell am start -a "android.intent.action.MAIN" -n "org.yourself.watch/org.yourself.watch.Tasks"
fi

exit 0

