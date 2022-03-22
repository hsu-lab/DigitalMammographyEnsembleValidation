#!/usr/bin/env /Users/bzhu/groovy-3.0.4/bin/groovy

@Grab('mysql:mysql-connector-java:5.1.49')
@GrabConfig(systemClassLoader = true)

import com.mysql.jdbc.*
import groovy.sql.*
import groovy.time.*
import java.util.Date
import Sc2MammoImg

NUM_EXAMS_PER_FILE = -1

db_host = "localhost"
db_name = "elmore_breast_study"
db_user = "bzhu"
db_pass = "F4T2RC^Jd@cG"

TOP_DATA_OUT_DIR="."

// default survey table
SURVEY_TABLE = ''

// cureent DICOM image collections
DICOM_STUDY_TABLES = ['deid_dicom_study']

// sc2 util functions
sc2_img = new Sc2MammoImg()

/*
Local image dirs:
  dicom_study':     /data/r37deid
  pos_dicom_study:  /data/athena-pos-2, /data/athena-pos-3-deid
  neg_dicom_study:  /Users/Shared/Data/athena-neg-deid, /Users/Shared/Data/athena-neg-2-deid
*/


def proc_one(db, dicomStudyTables, surveyTable, acc_num_h) {

	def mc = ['valid': false, 'err': 'unknonw']

	def rid = ''
	def r = db.firstRow("select research_id from " + SURVEY_TABLE + " where acc_num_h ='" + acc_num_h + "'")
	if(r) {
		rid = r.research_id
	}
	else {
		mc['err'] = 'Could not find record in survey.'
		return mc
	}

	def exam_list = []
	prev_acc_num = ''
	db.eachRow("select ACC_NUM_H, EXAM_DATE from " + surveyTable + " where RESEARCH_ID = '" + rid + "' order by EXAM_DATE asc") {
		if(!it.ACC_NUM_H) {
			// skip it
		}
		else if(it.ACC_NUM_H == prev_acc_num) {
			// skip it
		}
		else {
			exam_list.add(['acc_num': it.ACC_NUM_H, 'exam_date': it.EXAM_DATE])
			prev_acc_num = it.ACC_NUM_H
		}
	}
	// System.err.println "rid=" + rid
	if(!exam_list) {
		System.err.println "  No exam is found in survey table."
		mc['err'] = 'No exam is found in survey.'
		return mc
	}

	// System.err.println "  exams = " + exam_list

	int cnt = 0
	def prev_date = null
	for(int i=0; i<exam_list.size(); i++) {

		def acc_num = exam_list[i]['acc_num']
		// System.err.println "  acc_num=" + acc_num

		mc = sc2_img.get_mammo_exam_info(db, dicomStudyTables, surveyTable, acc_num)
		if(mc?['valid']) {
			cnt = cnt + 1
			mc['exams_metadata']['examIndex'] = cnt
			if(prev_date) {
				int nd = exam_list[i]['exam_date'] - prev_date
				mc['exams_metadata']['daysSincePreviousExam'] = nd
				// System.err.println "  days between " + exam_list[i]['exam_date'] + ", " + prev_date + " is: " + nd
				prev_date = exam_list[i]['exam_date']
			}
			else {
				prev_date = exam_list[i]['exam_date']
			}

			// construct mc['images_crosswalk']
			mc['images_crosswalk'] = []
			mc['images_crosswalk'].add(['subjectId': rid, 'examIndex': cnt, 'imageIndex': 1, 'view': mc['dcm_4_images']['1']['view'], 'laterality': mc['dcm_4_images']['1']['laterality'], 'filename': rid + "_" + cnt + "_1.dcm"])
			mc['images_crosswalk'].add(['subjectId': rid, 'examIndex': cnt, 'imageIndex': 2, 'view': mc['dcm_4_images']['2']['view'], 'laterality': mc['dcm_4_images']['2']['laterality'], 'filename': rid + "_" + cnt + "_2.dcm"])
			mc['images_crosswalk'].add(['subjectId': rid, 'examIndex': cnt, 'imageIndex': 3, 'view': mc['dcm_4_images']['3']['view'], 'laterality': mc['dcm_4_images']['3']['laterality'], 'filename': rid + "_" + cnt + "_3.dcm"])
			mc['images_crosswalk'].add(['subjectId': rid, 'examIndex': cnt, 'imageIndex': 4, 'view': mc['dcm_4_images']['4']['view'], 'laterality': mc['dcm_4_images']['4']['laterality'], 'filename': rid + "_" + cnt + "_4.dcm"])

			// construct mc['dcm_filename_mappings']
			mc['dcm_filename_mappings'] = []
			mc['dcm_filename_mappings'].add(mc['dcm_4_images']['dcm_table'] + "|" + acc_num + "|" + mc['dcm_4_images']['1']['ori_dcm_filename'] + "|" + rid + "_" + cnt + "_1.dcm")
			mc['dcm_filename_mappings'].add(mc['dcm_4_images']['dcm_table'] + "|" + acc_num + "|" + mc['dcm_4_images']['2']['ori_dcm_filename'] + "|" + rid + "_" + cnt + "_2.dcm")
			mc['dcm_filename_mappings'].add(mc['dcm_4_images']['dcm_table'] + "|" + acc_num + "|" + mc['dcm_4_images']['3']['ori_dcm_filename'] + "|" + rid + "_" + cnt + "_3.dcm")
			mc['dcm_filename_mappings'].add(mc['dcm_4_images']['dcm_table'] + "|" + acc_num + "|" + mc['dcm_4_images']['4']['ori_dcm_filename'] + "|" + rid + "_" + cnt + "_4.dcm")

			// System.err.println "    exam is included."
			if(acc_num == acc_num_h) {
				mc['valid'] = true
				mc['err'] = ''
				return mc
			}
		}

		if(acc_num == acc_num_h) {
			return mc
		}
	}

	['valid': false, 'err': 'unknown']
}

def save_exams(mammoExams, collIdx) {

	def metadata_fname = "exams_metadata.tsv"
	def crosswalk_fname = "images_crosswalk.tsv"
	def dcm_mapping_fname = "exams_dcm_file_mappings.txt"

	def coll_dir = ""

	if(NUM_EXAMS_PER_FILE <= 0) {
		coll_dir = TOP_DATA_OUT_DIR
	}
	else {
		if(collIdx <= 0) {
			println "invalid coll index: " + collIdx
			System.exit(1)
		}
		coll_dir = TOP_DATA_OUT_DIR + "/coll_" + collIdx
	}

	if(!(new File(coll_dir + "/metadata").exists())) new File(coll_dir + "/metadata").mkdirs()
	if(!(new File(coll_dir + "/images").exists())) new File(coll_dir + "/images").mkdirs()

	mf = new File(coll_dir + "/metadata/" + metadata_fname)
	mf.text = ''
	mf << Sc2MammoImg.METADATA_ATTS.join('\t') << "\n"
	
	
	cf = new File(coll_dir + "/metadata/" + crosswalk_fname)
	cf.text = ''
	cf << Sc2MammoImg.CROSSWALK_ATTS.join('\t') << "\n"
	
	for(int i=0; i<mammoExams.size(); i++) {
	
		def ds = []
	
		for(int k=0; k<Sc2MammoImg.METADATA_ATTS.size(); k++) {
			ds.add(mammoExams[i]['exams_metadata'][Sc2MammoImg.METADATA_ATTS[k]])
		}
		mf << ds.join('\t') << "\n"
	
		for(int j=0; j < mammoExams[i]['images_crosswalk'].size(); j++) {
			ds = []
			for(int k=0; k<Sc2MammoImg.CROSSWALK_ATTS.size(); k++) {
				ds.add(mammoExams[i]['images_crosswalk'][j][Sc2MammoImg.CROSSWALK_ATTS[k]])
			}
			cf << ds.join('\t') << "\n"
		}
	
	}
	
	df = new File(coll_dir + "/" + dcm_mapping_fname)
	df.text = ''
	df << "deid_coll|acc_num|ori_filename|new_filename" << "\n"
	mammoExams.each {
		it['dcm_filename_mappings'].each { e->
			df << e << "\n"
		}
	}
	
	println "Output files:"
	println "  " + coll_dir + "/metadata/" + metadata_fname
	println "  " + coll_dir + "/metadata/" + crosswalk_fname
	println "  " + coll_dir + "/" + dcm_mapping_fname

}

def patient_cnt(mammoExams) {
	def pids = [] as Set
	mammoExams.each {
		pids.add(it['images_crosswalk']['subjectId'])
	}
	pids.size()
}

// ----
// main
// ----

db = null

try {
  db  = Sql.newInstance('jdbc:mysql://' + db_host  + ':3306/' + db_name, db_user, db_pass,  'com.mysql.jdbc.Driver')
  println "Connecting to mysql db succeeded."
}
catch(all) {
  println "Failed to connect to survey data source db: " + all.message
  System.exit(1)
}
db.connection.autoCommit = false

accnum_list = []
if(args.size() == 1) {
	System.err.println "Take all exams in survey table."
	SURVEY_TABLE = args[0]
	db.eachRow("select distinct acc_num_h from " + SURVEY_TABLE) { accnum_list.add(it.acc_num_h) }
}
else if(args.size() == 2) {
	SURVEY_TABLE = args[0]
	(new File(args[1])).eachLine { accnum_list.add(it) }
}
else {
    println "Usage: get_sc2_by_accnum.groovy SURVEY_TABLE [ACCNUM_LIST_FILE]"
	println "  If acc num list is not supplied, all exams in the survey table will be processed."
    System.exit(0)
}

// accnum_list.each { println it }
// println "Number of acc_num's: " + accnum_list.size()

excluded_exams = []

mammo_exams = []
cnt = 1
for(int i=0; i<accnum_list.size(); i++) {

    def exam = [:]
	exam = proc_one(db, DICOM_STUDY_TABLES, SURVEY_TABLE, accnum_list[i])
	// println "  return num of exams=" + exams.size()
	if(exam?['valid']) {
		mammo_exams.add(exam)
	}
	else {
		excluded_exams.add(['acc_num_h': accnum_list[i], 'reason': exam['err']])
	}
		
	if(NUM_EXAMS_PER_FILE > 0) {
		if(mammo_exams.size() == NUM_EXAMS_PER_FILE) {
			save_exams(mammo_exams, cnt)
			mammo_exams = []
			++cnt
		}
	}

	if((i+1)%50 == 0) {
		System.err.println "processed: " + (i+1) + " / " + accnum_list.size()
	}

}

if(mammo_exams) save_exams(mammo_exams, cnt)

if(excluded_exams) {
	println ""
	println "excluded exams: " + excluded_exams.size()
	excluded_exams.each { println it['acc_num_h'] + ": " + it['reason'] }
}


