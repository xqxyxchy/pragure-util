#!/bin/bash
# should rinning by root

show_usage="args: [-h, -m, -c]\
                                  [--home, --memory=, --command]"

p_home="."
p_memory="128M"
p_command="start"

GETOPT_ARGS=`getopt -o h:d:m:c: -al home:,memory:,command: -- "$@"`
eval set -- "$GETOPT_ARGS"

while [ -n "$1" ]
do
        case "$1" in
		-h|--home) p_home=$2; shift 2;;
                -m|--memory) p_memory=$2; shift 2;;
                -c|--command) p_command=$2; shift 2;;
                --) break ;;
                *) echo $1,$2,$show_usage; break ;;
        esac
done

start() {
	echo "---------------------------------------------"
	echo "--------------monitor starting----------------"
	echo "---------------------------------------------"

	java -Xms$p_memory -Xmx$p_memory -jar $p_home/monitor.jar & echo $! > $p_home/monitor.pid
}

stop() {
	echo "---------------------------------------------"
	echo "--------------monitor stoping----------------"
	echo "---------------------------------------------"

	pid=$(cat $p_home/monitor.pid)
	kill -9 ${pid}
}

# See how we were called.
case "$p_command" in
  start)
        start
        ;;
  stop)
        stop
        ;;
  restart|reload)
        stop
        start
        ;;
  *)
        echo $"Usage: $0 {start|stop|restart|reload}"
        exit 1
esac

exit

