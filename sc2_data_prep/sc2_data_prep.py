#!/usr/bin/python3

import os
import sys
import shutil
import glob
import magic
import sys
import pydicom as dicom
from pydicom.tag import Tag
import numpy as np
import pandas as pd
import traceback


use_ori_fname = False
num_subjs_per_coll = -1
output_dir = "."

REQUIRED_SERIES = ['R CC', 'L CC', 'R MLO', 'L MLO']
METADATA_ATTS = [
  'subjectId',
  'examIndex',
  'daysSincePreviousExam',
  'cancerL',
  'cancerR',
  'invL',
  'invR',
  'age',
  'implantEver',
  'implantNow',
  'bcHistory',
  'yearsSincePreviousBc',
  'previousBcLaterality',
  'reduxHistory',
  'reduxLaterality',
  'hrt',
  'antiestrogen',
  'firstDegreeWithBc',
  'firstDegreeWithBc50',
  'bmi',
  'race'
]
REQUIRED_METADATA_ATTS_IN_CSV = ['subjectId', 'examIndex', 'daysSincePreviousExam', 'age', 'yearsSincePreviousBc', 'bmi', 'race']
METADATA_ATT_DEFAULT_VAL = {
    'cancerL': '*',
    'cancerR': '*',
    'invL': '*',
    'invR': '*',
    'implantEver': '.',
    'implantNow': '.',
    'bcHistory': 0,
    'previousBcLaterality': 3,
    'reduxHistory': '.',
    'reduxLaterality': '.',
    'hrt': 9,
    'antiestrogen': 9,
    'firstDegreeWithBc': 9,
    'firstDegreeWithBc50': 9
  }

CROSSWALK_ATTS = [
  'subjectId',
  'examIndex',
  'imageIndex',
  'view',
  'laterality',
  'filename'
]
ACC_NUM_TAG = '00080050'
SERIES_DESC_TAG = '0008103E'
# 'age': '00101010',

def getTagVal(ds, t):
  v = None
  if (t[0:4], t[4:]) in ds: v = ds[t[0:4], t[4:]].value
  if v is not None: return str(v)
  return ""

def parse_dicom_files(dcmDir):

  img_exams = {}
  print("dcmDir=" + dcmDir)
  for path, currentDirectory, files in os.walk(dcmDir):
    for f in files: 
      fp = os.path.join(path, f)
      if magic.from_file(fp).lower().startswith("dicom"):
        try:
          ds = dicom.dcmread(fp)
          acc_num = getTagVal(ds, ACC_NUM_TAG)
          series_desc = getTagVal(ds, SERIES_DESC_TAG)
          if not acc_num:
            print("Error: no Accession No. is found. file=" + f, file=sys.stderr)
          elif not series_desc:
            print("Error: no series description is found. file=" + f, file=sys.stderr)
          else:
            if series_desc.upper() in REQUIRED_SERIES:
              if not acc_num in img_exams: img_exams[acc_num] = {}
              if not series_desc.upper() in img_exams[acc_num]: img_exams[acc_num][series_desc.upper()] = fp
            else:
              print("Info: not a required series. file skipped. file=" + f, file=sys.stderr)
        except Exception as e:
          print("Error: failed to open dcm file for read. file=" + f, file=sys.stderr)
          print("  " + str(e), file=sys.stderr)

  valid_exams = {}
  for acc in img_exams:
    # img_exams[acc]
    if len(set(img_exams[acc].keys())) == 4:
      valid_exams[acc] = img_exams[acc]
    else:
      print("Info: no required 4 series. The imaging study is ignored. acc_num=" + acc, file=sys.stderr)

  return valid_exams

def to_metadata_strlist(r):

  m = {}

  for fld in REQUIRED_METADATA_ATTS_IN_CSV:
    m[fld] = r[fld]

  for fld in METADATA_ATT_DEFAULT_VAL:
    if fld in r:
      m[fld] = r[fld]
    else:
      m[fld] = METADATA_ATT_DEFAULT_VAL[fld]

  ret_list = []
  for fld in METADATA_ATTS:
    ret_list.append(str(m[fld]))

  return ret_list

def get_dest_img_fname(oriDicomFnameWithPath, surveyRow, imgIdx):
  if use_ori_fname:
    img_fnam = os.path.basename(oriDicomFnameWithPath)
  else:
    img_fnam =  surveyRow['subjectId'] + "_" + str(surveyRow['examIndex']) + "_" + str(imgIdx) + ".dcm"
  return img_fnam

def find_nul_val_field(r, attList):
  for t in attList:
    if r[t] is None: return t
    if t == "subjectId":
      if not bool(r[t]): return t
    else: # the rest are number field
      if np.isnan(r[t]): return t
  return ""

def gen_sc2_data(imgExams, srvyDf):

  subj_ids = set(srvyDf['subjectId'].tolist())
  print("Number of subjects in survey data: " + str(len(subj_ids)), file=sys.stderr)

  coll_counter = 1

  if num_subjs_per_coll > 0:
    print("Working on collection #" + str(coll_counter) + "...", file=sys.stderr)
    metadata_file_dir = output_dir + "/coll_" + str(coll_counter) + "/metadata"
    images_dir = output_dir + "/coll_" + str(coll_counter) + "/images"
  else:
    metadata_file_dir = output_dir + "/metadata"
    images_dir = output_dir + "/images"

  if not os.path.exists(metadata_file_dir): os.makedirs(metadata_file_dir)
  if not os.path.exists(images_dir): os.makedirs(images_dir)

  mtaf = open(metadata_file_dir + "/exams_metadata.tsv", "w")
  imgf = open(metadata_file_dir + "/images_crosswalk.tsv", "w")

  # headers for the metadata files
  mtaf.write("\t".join(METADATA_ATTS) + "\n")
  imgf.write("\t".join(CROSSWALK_ATTS) + "\n")

  subj_cnt = 0
  for sid in subj_ids:

    df = srvyDf.loc[srvyDf.subjectId == sid]

    has_valid_exam = False
    for i in range(0, df.shape[0]):
  
      r = df.iloc[i]
      acc_num = r['accessionNo']

      if acc_num in imgExams:

        # check req fields
        fldnam = find_nul_val_field(r, REQUIRED_METADATA_ATTS_IN_CSV)
        if not bool(fldnam):  # if no field is empty val field is found
  
          m = to_metadata_strlist(r)
          mtaf.write("\t".join(m) + "\n")
    
          # -----------
          # 4 img files
          # -----------
          # R CC
          img_fnam = get_dest_img_fname(imgExams[acc_num]['R CC'], r, 1)
          ic = [r['subjectId'], str(r['examIndex']), "1", "CC", "R", img_fnam]
          imgf.write("\t".join(ic) + "\n")
          shutil.copy2(imgExams[acc_num]['R CC'], images_dir + "/" + img_fnam)
      
          # L CC
          img_fnam = get_dest_img_fname(imgExams[acc_num]['L CC'], r, 2)
          ic = [r['subjectId'], str(r['examIndex']), "2", "CC", "L", img_fnam]
          imgf.write("\t".join(ic) + "\n")
          shutil.copy2(imgExams[acc_num]['L CC'], images_dir + "/" + img_fnam)
      
          # R MLO
          img_fnam = get_dest_img_fname(imgExams[acc_num]['R MLO'], r, 3)
          ic = [r['subjectId'], str(r['examIndex']), "3", "MLO", "R", img_fnam]
          imgf.write("\t".join(ic) + "\n")
          shutil.copy2(imgExams[acc_num]['R MLO'], images_dir + "/" + img_fnam)
      
          # L MLO
          img_fnam = get_dest_img_fname(imgExams[acc_num]['L MLO'], r, 4)
          ic = [r['subjectId'], str(r['examIndex']), "4", "MLO", "L", img_fnam]
          imgf.write("\t".join(ic) + "\n")
          shutil.copy2(imgExams[acc_num]['L MLO'], images_dir + "/" + img_fnam)
  
          has_valid_exam = True

        else:
          print("Info: No required data is found for the column, " + fldnam + ". accessionNo=" + acc_num, file=sys.stderr)


      else:
        print("Info: no imaging exam is found for the survey. accessionNo=" + acc_num, file=sys.stderr)
  
    if has_valid_exam:
      subj_cnt = subj_cnt +1
      if num_subjs_per_coll > 1:
        if subj_cnt == num_subjs_per_coll:

          coll_counter = coll_counter + 1
          print("Working on collection #" + str(coll_counter) + "...", file=sys.stderr)
  
          metadata_file_dir = output_dir + "/coll_" + str(coll_counter) + "/metadata"
          images_dir = output_dir + "/coll_" + str(coll_counter) + "/images"
  
          if not os.path.exists(metadata_file_dir): os.makedirs(metadata_file_dir)
          if not os.path.exists(images_dir): os.makedirs(images_dir)
  
          mtaf.close()
          mtaf = open(metadata_file_dir + "/exams_metadata.tsv", "w")
          imgf.close()
          imgf = open(metadata_file_dir + "/images_crosswalk.tsv", "w")
  
          subj_cnt = 0
    else:
      print("Info: no valid exma is found for the subject. subjectId=" + sid, file=sys.stderr)

  if not mtaf.closed: mtaf.close()
  if not imgf.closed: imgf.close()

  # if the last coll is empty or no valid exams is found in the single collection case, remove the directories.
  if len(os.listdir(images_dir)) == 0:
    if num_subjs_per_coll > 0:
      collname = output_dir + "/coll_" + str(coll_counter)
      print("The last collection has no data. The collection, " + collname + ", is removed.", file=sys.stderr)
      shutil.rmtree(collname)
      if coll_counter == 1:
        print("No valid matching data is found among imaging files and survey data. No output data is produced.", file=sys.stderr)
    else:
      print("No valid matching data is found among imaging files and survey data. No output data is produced.", file=sys.stderr)
      shutil.rmtree(images_dir)
      shutil.rmtree(metadata_file_dir)

  return 0

def usage():
  print("Usage: sc2_data_prep.py -imgdir=IMG_DIR -survey=SURVEY_CSV_FILE [-use_ori_imgname=true] [-num_subjs_per_collection=NUM]", file=sys.stderr)

# ----
# main
# ----
img_dir = ''
survey_file = ''
for s in sys.argv:
  if s.startswith("-imgdir="):
    img_dir = s[8:]
  elif s.startswith("-survey="):
    survey_file = s[8:]
  elif s.startswith("-use_ori_imgname="):
    if s[17:].lower() == "true": use_ori_fname = True
  elif s.startswith("-exams_per_collection="):
    num_subjs_per_coll = int(s[22:])

if not bool(img_dir):
  print("Error: No imgaing directory is specified.", file=sys.stderr)
  usage()
  sys.exit(0)
if not bool(survey_file):
  print("Error: No survey file is specified.", file=sys.stderr)
  usage()
  sys.exit(0)
if num_subjs_per_coll is None or num_subjs_per_coll == 0:
  print("Error: value for 'exams_per_collection' is invalid.", file=sys.stderr)
  usage()
  sys.exit(0)

try:

  print("Parsing DICOM files...", file=sys.stderr)
  print("img_dir=" + img_dir)
  img_exams = parse_dicom_files(img_dir)
  if not bool(img_exams):
    print("No valid image file is found.", file=sys.stderr)
    sys.exit(0)

  print("Reading survey data...", file=sys.stderr)
  survey_df = pd.read_csv(survey_file, dtype={'accessionNo': str, 'subjectId': str})

  # check required columns in survey data
  for c in REQUIRED_METADATA_ATTS_IN_CSV:
    if c not in survey_df.columns:
      print("The column is not found in the CSV file: " + c, file=sys.stderr)
      sys.exit(1)

  print("combining image data and survey data...", file=sys.stderr)
  gen_sc2_data(img_exams, survey_df)

  print("gen_sc2_data.py done", file=sys.stderr)

except Exception as e:
  print("Error: " + str(e), file=sys.stderr)
  traceback.print_exc()

