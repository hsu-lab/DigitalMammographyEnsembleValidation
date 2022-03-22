#!/bin/bash

# top directory for dcm images or tar files.
IMG_DIRS=(/Users/bzhu/athena_wk/sc2_data_prep/bin/test_data/d1 /Users/bzhu/athena_wk/sc2_data_prep/bin/test_data/d2)

# change it if you changed the mapping file name
MAPPING_FILE="exams_dcm_file_mappings.txt"


# ----------------------------
# do not make any change below
# ----------------------------

tar_file_mode="0"
cur_img_dir=""
tar_filepath=""

function get_tar_filepath() {
  tar_filepath=""
  for d in ${IMG_DIRS[*]}
  do
    if [ -f "$d/$1.tar.gz" ]; then
      tar_filepath="$d/$1.tar.gz"
    fi
  done
}

function get_img_dir() {
  cur_img_dir=""
  for d in ${IMG_DIRS[*]}
  do
    if [ -d "$d/$1" ]; then
	  cur_img_dir="$d/$1"
	fi
  done
}


if (( $# == 1 )); then
  if [ "$1" = "-tar" ]; then
    tar_file_mode="1"
  fi
else
  echo "get_sc2_images.sh -tar|-dir"
  exit 1
fi

if [ "$tar_file_mode" = "1" ]; then
  echo "tar file mode"
else
  echo "dir mode"
fi

tmpfile=$MAPPING_FILE".tmp"
tail -n +2 $MAPPING_FILE > $tmpfile

while IFS= read -r line
do
  echo "$line"

  if [ -z $line ]; then
	echo "  empty line"
    continue
  fi

  ds=($(echo $line | tr "|" "\n"))
  if [ -z ${ds[1]} ]; then
	echo "  no accnum is found"
    continue
  fi

  if [ "$tar_file_mode" = "1" ]; then
	# copy file from a tar file
    if [ ! -d "${ds[1]}" ]; then
	  get_tar_filepath "${ds[1]}"
	  if [ ! -z "$tar_filepath" ]; then
        echo "  copy tar file"
        cp "$tar_filepath" .
        tar -zxf ${ds[1]}".tar.gz"
	  fi
    fi
    echo "  copy image file"
    cp ${ds[1]}"/"${ds[2]} images/${ds[3]}

  else
    # copy files from dir
	get_img_dir "${ds[1]}"
	if [ ! -z "$cur_img_dir" ]; then
	  echo "  copy image file: ${ds[3]}"
	  cp $cur_img_dir"/"${ds[2]} images/${ds[3]}
	fi
  fi


done < "$tmpfile"
echo "copying image files done"

echo "clean dirs and ar files..."
while IFS= read -r line
do
  if [ -z $line ]; then
    continue
  fi
  ds=($(echo $line | tr "|" "\n"))
  if [ -z ${ds[1]} ]; then
    continue
  fi
  rm -f ${ds[1]}".tar.gz"
  rm -rf ${ds[1]}
done < "$tmpfile"

echo "remove tmp file"
rm $tmpfile

