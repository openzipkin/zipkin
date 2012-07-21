#! /bin/bash

usage() {
cat << EOF
usage: $0 options

This script runs the hadoop queries

OPTIONS:
   -h      Show this message
   -d      Date, either as one date, or start date and end date, separated by commas
   -o      Output directory
EOF
}

TIMES=
STARTTIME=
ENDTIME=
OUTPUT=
while getopts "hd:o:" OPTION
do
     case $OPTION in
         h)
             usage
             exit 1
             ;;
         d)
             TIMES=(`echo $OPTARG | tr ',' ' '`)
             ;;
         o)
             OUTPUT=$OPTARG
             ;;
     esac
done

size=${#TIMES[@]}
echo "Dates: ${TIMES[@]}"

if [[ -z $OUTPUT ]] || [[ $size < 0 ]] || [[ $size > 2 ]] 
then
    usage
    exit 1
fi

if [ $size == 1 ]; then
    STARTTIME=${TIMES[0]}
    ENDTIME=${TIMES[0]}
else
    STARTTIME=${TIMES[0]}
    ENDTIME=${TIMES[1]}
fi


echo "Output: $OUTPUT"
echo "Start and end time: $STARTTIME - $ENDTIME"

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "From run.sh $DIR"

#ENDTIME="2012-07-19T01:00"
FAIL=0

$DIR/scald.rb --hdfs com.twitter.zipkin.hadoop.sources.Preprocessed --date $ENDTIME $ENDTIME

$DIR/run_job.sh -j WorstRuntimes -d $ENDTIME -o $OUTPUT/WorstRuntimes &
$DIR/run_job.sh -j MemcacheRequest -d $ENDTIME -o $OUTPUT/MemcacheRequest &

$DIR/run_job.sh -j FindNames -p -d $ENDTIME

$DIR/run_job.sh -j PopularKeys  -d $ENDTIME -o $OUTPUT/PopularKeys &
$DIR/run_job.sh -j PopularAnnotations  -d $ENDTIME -o $OUTPUT/PopularAnnotations  &
$DIR/run_job.sh -j WhaleReport -d $ENDTIME -o $OUTPUT/WhaleReport &

$DIR/run_job.sh -j FindIDtoName -p  -d $ENDTIME

$DIR/run_job.sh -j DependencyTree -d $ENDTIME -o $OUTPUT/DependencyTree  &
$DIR/run_job.sh -j Timeouts -s "--error_type finagle.timeout" -o $OUTPUT/Timeouts -d $ENDTIME &
$DIR/run_job.sh -j Timeouts -s "--error_type finagle.retry" -o $OUTPUT/Retries -d $ENDTIME &

wait

echo "Finished all jobs!"