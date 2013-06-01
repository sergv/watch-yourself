#!/bin/bash
#
# File: build.sh
#
# Created: Saturday,  1 June 2013
#

ant debug && ant installd && adb shell am start -a "android.intent.action.MAIN" -n "org.yourself.watch/org.yourself.watch.Tasks"

exit 0

