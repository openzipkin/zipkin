#! /bin/bash

usage() {
cat << EOF
usage: $0 options

This script runs the hadoop queries

OPTIONS:
   -h      Show this message
   -j      Name of the job
   -p      Flag to denote if the job being run is a preprocessor
   -s      Any settings you want to pass along
   -d      Date
   -o      Output directory
EOF
}

JOBNAME=
ISPREPROCESSOR=
DATE=
SETTINGS=
OUTPUTDIR=
while getopts "hj:ps:d:o:" OPTION
do
    case $OPTION in
	h)
	    usage
	    exit 1
	    ;;
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

echo "From run_job.sh $DIR"

if [ "$ISPREPROCESSOR" == "1" ]; then
    $DIR/scald.rb --hdfs com.twitter.zipkin.hadoop.sources.$JOBNAME $SETTINGS --date $DATE
    if [ "$?" != 0 ]; then
        echo "Job $JOBNAME failed; exiting"
        exit 1
    fi
else
    $DIR/scald.rb --hdfs com.twitter.zipkin.hadoop.$JOBNAME $SETTINGS --date $DATE $DATE --output $OUTPUTDIR
    if [ "$?" == "0" ]; then
        echo "Job $JOBNAME succesfully completed"
    else
        echo "Job $JOBNAME failed; exiting"
        exit 1
    fi
fi