#!/bin/bash
#=====================================================================================
# Usage:        ./start.sh [jvmArgs]
# Parameters:   jvmArgs
#                   additional arguments to the JVM process starting the server
# Description:  Starts the server and restarts it depending on returned exit code.
#=====================================================================================

loop() {
  while true; do
    java -Xms72m -Xmx72m -XX:+UseNUMA $@ -cp "libs/*" com.aionemu.chatserver.ChatServer
    err=$?
    case $err in
      0) # regular shutdown
        echo "Chat server stopped."
        break
        ;;
      1) # critical config or build error
        >&2 echo "Chat server stopped with a fatal error."
        break
        ;;
      2) # restart request
        echo "Restarting chat server..."
        ;;
      130) # SIGINT / Ctrl+C
        echo "Chat server process was stopped."
        break
        ;;
      137|143) # 137=SIGKILL, 143=SIGTERM
        echo "Chat server process was killed."
        break
        ;;
      *) # other
        >&2 echo "Chat server has terminated abnormally (code: $err), restarting in 5 seconds."
        sleep 5
        ;;
    esac
  done
  exit $err
}

pid=$(jps -l | grep com.aionemu.chatserver.ChatServer | awk '{print $1}')
if [[ -n $pid ]]; then
  echo "Chat server is already running (PID $pid)"
  read -p "Shut it down? (y/n) " answer
    if [[ $answer =~ ^y(es)?$ ]]; then
      if [[ -n $MSYSTEM ]]; then # MinGW (like Git Bash on Windows)
        /bin/kill -fW -SIGINT $pid # -W parameter tells kill command it's a Windows PID
      else
        kill -SIGINT $pid
      fi
      echo "Sent stop signal"
      until ! (jps -q | grep ^$pid$ > /dev/null); do echo "Waiting for shutdown..."; sleep 1; done 
    else
      echo "Aborting server start"
      exit 1
    fi
fi
cd "$(dirname "$(readlink -f "$0")")"
loop $@
