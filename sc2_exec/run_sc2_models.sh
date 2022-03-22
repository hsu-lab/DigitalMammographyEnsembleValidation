#!/bin/bash

MODELS=(9646823 9648211 9648881 9648888 9648889 9648917 9650054 9650055 9650232 9650290 9650300)

CUR_DIR=`pwd`

if [ ! -f $CUR_DIR/metadata/images_crosswalk.tsv ]; then
    echo "image-crosswak file not found: $CUR_DIR/metadata/images_crosswalk.tsv"
    exit 1
fi

if [ ! -f $CUR_DIR/metadata/exams_metadata.tsv ]; then
    echo "metadata file not found: $CUR_DIR/metadata/exams_metadata.tsv"
    exit 1
fi

if [ ! -d $CUR_DIR/images ]; then
    echo "image directory not found: $CUR_DIR/images"
    exit 1
fi

if [ ! -d $CUR_DIR/modelState ]; then
    mkdir $CUR_DIR/modelState
fi


rm -f failed_models.txt

total_dur=0
for i in ${MODELS[*]}
do
	SECONDS=0

    echo "------------------------------------------------"
    echo "running docker.synapse.org/syn7887972/$i/scoring"

    if [ ! -d $CUR_DIR/output ]; then
        mkdir $CUR_DIR/output
    fi
    if [ ! -d $CUR_DIR/output/$i ]; then
        mkdir $CUR_DIR/output/$i
    fi

    if [ ! -d $CUR_DIR/scratch ]; then
        mkdir $CUR_DIR/scratch
    fi
    if [ ! -d $CUR_DIR/scratch/$i ]; then
        mkdir $CUR_DIR/scratch/$i
    fi

    nvidia-docker run \
    -e "NUM_GPU_DEVICES=2" \
	-e "GPUS=/dev/nvidia0;/dev/nvidia1" \
    -e "NUM_CPU_CORES=32" \
    -v $CUR_DIR/images:/inferenceData:ro \
    -v $CUR_DIR/metadata/images_crosswalk.tsv:/metadata/images_crosswalk.tsv:ro \
    -v $CUR_DIR/metadata/exams_metadata.tsv:/metadata/exams_metadata.tsv:ro \
    -v $CUR_DIR/modelState:/modelState \
    -v /dev/null:/dev/raw1394 \
    -v $CUR_DIR/scratch/$i:/scratch:rw \
    -v $CUR_DIR/output/$i:/output:rw \
	--device=/dev/nvidia0:/dev/nvidia0:rw \
    --device=/dev/nvidia1:/dev/nvidia1:rw \
    docker.synapse.org/syn7887972/$i/scoring \
    /sc2_infer.sh
    dur=$SECONDS
    echo "  running time for $i: $(($dur/60)) minutes and $(($dur%60)) seconds"

    if [ ! -f $CUR_DIR/output/$i/predictions_exams.tsv ]; then
        echo "Model #$i failed. No output file is found."
		echo "Model #$i failed." >> failed_models.txt
	else
    	NUM_RESULTS=`wc -l < $CUR_DIR/output/$i/predictions_exams.tsv`
    	if [ $((NUM_RESULTS)) -le 1 ]; then
        	echo "Model #$i failed. No result is found in outout file, $CUR_DIR/output/$i/predictions_exams.tsv."
			echo "Model #$i failed." >> failed_models.txt
    	fi
    fi

    echo ""
    echo ""
    echo ""

	sleep 30
	total_dur=$(($total_dur+$dur+30))

done
echo ""
echo ""
echo ""
echo "Total running time: $(($total_dur/60)) minutes and $(($total_dur%60)) seconds"
echo ""

