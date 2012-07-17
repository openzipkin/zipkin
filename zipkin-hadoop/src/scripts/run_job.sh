#! /bin/bash

JOBNAME=
ISPREPROCESSOR=
DATE=
SETTINGS=
OUTPUTDIR=
HOST=
while getopts “j:ps:d:n:o:” OPTION
do
    case $OPTION in
        j)
            JOBNAME=$OPTARG
            ;;
        p)
            ISPREPROCESSOR=1
            ;;
        s)
            SETTINGS=$OPTARG
            ;;
	d)
	    DATE=$OPTARG
	    ;;
	n)
	    HOST=$OPTARG
	    ;;
        o)
            OUTPUTDIR=$OPTARG
            ;;
    esac
done
echo "Job: $JOBNAME"
echo "Is Pre: $ISPREPROCESSOR"
echo "Settings: $SETTINGS"
echo "Output: $OUTPUTDIR"
echo "Date: $DATE"

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ "$ISPREPROCESSOR" == "1" ]; then
    $DIR/scald.rb --hdfs com.twitter.zipkin.hadoop.sources.$JOBNAME $SETTINGS --date $DATE
    if [ "$?" != 0 ]; then
        echo "Job $JOBNAME failed; exiting"
        exit 1
    fi
else
    $DIR/scald.rb --hdfs com.twitter.zipkin.hadoop.$JOBNAME $SETTINGS --date $DATE --output $OUTPUTDIR
    if [ "$?" == "0" ]; then
        echo "Job $JOBNAME succesfully completed"
    else
        echo "Job $JOBNAME failed; exiting"
        exit 1
    fi
fi

