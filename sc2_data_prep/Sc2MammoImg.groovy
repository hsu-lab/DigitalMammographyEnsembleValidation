
class Sc2MammoImg {

	// public static final DICOM_STUDY_TABLES = ['deid_dicom_study', dicom_study', 'pos_dicom_study', 'neg_dicom_study', 'tmp_100_dicom_study']

    public static final METADATA_ATTS = [
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

	public static final CROSSWALK_ATTS = [
	  'subjectId',
	  'examIndex',
	  'imageIndex',
	  'view',
	  'laterality',
	  'filename'
	]
	
	private def get_4_mammo_images(db, acc_num, study_tbl, series_tbl, instance_tbl) {
	
		def cw = ['valid':false, 'err': 'unknown']
	
		// identify the 'study_uid' for the acc_num that has required 4 series.
		def study_uids = []
		db.eachRow("select distinct study_uid from " + study_tbl + " where acc_num_h ='" + acc_num + "'") { study_uids.add(it.study_uid) }
		if(!study_uids) {
			// System.err.println "    No imaging study is found in '" + study_tbl + "'"
			cw['err'] = 'No imaging study is found.'
			return cw
		}
	
		// System.err.println "    acc_num=" + acc_num + " ==> uids=" + study_uids
		int found_cnt = 0
		String study_uid = ''
		for(int i=0; i<study_uids.size(); i++) {
			// println "  sql=" + "select count(*) as cnt from " + series_tbl + " where study_uid = '" + study_uids[i] + "' and series_desc in ('R CC', 'L CC', 'R MLO', 'L MLO')"
			if(db.firstRow("select count(*) as cnt from " + series_tbl + " where study_uid = '" + study_uids[i] + "' and series_desc in ('R CC', 'L CC', 'R MLO', 'L MLO')")?.cnt >= 4) {
				study_uid = study_uids[i]
				found_cnt = found_cnt + 1
			}
		}
	
		if(found_cnt == 0) {
			// System.err.println "    No required 4 series are found in '" + study_tbl + "' for exam. acc_num=" + acc_num
			cw['err'] = 'No required 4 series are found.'
			return cw
		}
		else if(found_cnt > 1) {
			System.err.println "    1+ qualified studies are found for the exam in '" + study_tbl + "' for exam. acc_num=" + acc_num
			cw['err'] = '1+ qualified studies are found for the exam'
			return cw
		}
	
		cw = get_mammo_study(db, study_uid, study_tbl, series_tbl, instance_tbl)
		cw
	}
	
	private def get_mammo_study(db, study_uid, study_tbl, series_tbl, instance_tbl) {
	
		def cw = ['valid': false, 'err': 'unknown']

		String new_dcm_filename = ''
	
		cw['age'] = null
		cw['dcm_4_images'] = [:]
		db.eachRow("select t2.age as age from " + study_tbl + " t1, " + series_tbl + " t2 where t1.study_uid = t2.study_uid and t1.study_uid = '" + study_uid + "'") {
			if(it.age != null) {
				cw['age'] = it.age
			}
		}
		if(cw['age'] == null) {
			// System.err.println "    No age is found in '" + study_tbl + "'"
			cw['err'] = 'No age is found.'
			return cw
		}
	
		// R CC
		String s = "select t1.research_id, t3.dicomfilename from " + study_tbl + " t1, " + series_tbl + " t2, " + instance_tbl + " t3 where " + \
		    " t1.study_uid = '" + study_uid + "' and " + \
			" t1.study_uid = t2.study_uid and " + \
			" t2.series_desc = 'R CC' and " + \
			" t2.series_uid = t3.series_uid"
		// println "sqlstr=" + s
		def r = db.firstRow(s)
		if(!r) {
			// System.err.println "    could not find R CC series in '"  + study_tbl + "'"
			cw['err'] = 'Could not find R CC series.'
			return cw
		}
		cw['dcm_4_images']['1'] = ['view': 'CC', 'laterality':'R', 'ori_dcm_filename': r.dicomfilename]
	
	
		// L CC
		s = "select t1.research_id, t3.dicomfilename from " + study_tbl + " t1, " + series_tbl + " t2, " + instance_tbl + " t3 where " +\
		    " t1.study_uid = '" + study_uid + "' and " +\
			" t1.study_uid = t2.study_uid and " +\
			" t2.series_desc = 'L CC' and " +\
			" t2.series_uid = t3.series_uid"
		r = db.firstRow(s)
		if(!r) {
			// System.err.println "    could not find R CC series in '"  + study_tbl + "'"
			cw['err'] = 'Could not find R CC series.'
			return cw
		}
		cw['dcm_4_images']['2'] = ['view': 'CC', 'laterality':'L', 'ori_dcm_filename': r.dicomfilename]
	
	
		// R MLO
		s = "select t1.research_id, t3.dicomfilename from " + study_tbl + " t1, " + series_tbl + " t2, " + instance_tbl + " t3 where " + \
		    " t1.study_uid = '" + study_uid + "' and " +\
			" t1.study_uid = t2.study_uid and " +\
			" t2.series_desc = 'R MLO' and " +\
			" t2.series_uid = t3.series_uid"
		r = db.firstRow(s)
		if(!r) {
			// System.err.println "    could not find R CC series in '"  + study_tbl + "'"
			cw['err'] = 'Could not find R CC series.'
			return cw
		}
		cw['dcm_4_images']['3'] = ['view': 'MLO', 'laterality':'R', 'ori_dcm_filename': r.dicomfilename]
	
	
		// L MLO
		s = "select t1.research_id, t3.dicomfilename from " + study_tbl + " t1, " + series_tbl  + " t2, " + instance_tbl + " t3 where " +\
		    " t1.study_uid = '" + study_uid + "' and " +\
			" t1.study_uid = t2.study_uid and " +\
			" t2.series_desc = 'L MLO' and " +\
			" t2.series_uid = t3.series_uid"
		r = db.firstRow(s)
		if(!r) {
			// System.err.println "    could not find R CC series in '"  + study_tbl + "'"
			cw['err'] = 'Could not find R CC series.'
			return cw
		}
		cw['dcm_4_images']['4'] = ['view': 'MLO', 'laterality':'L', 'ori_dcm_filename': r.dicomfilename]

		cw['valid'] = true
		cw['err'] = ''
	
		cw
	}
	
	def get_mammo_exam_info(db, dcmStudyTables, surveyTable, accNum) {
	
		// println "  accNum=" + accNum
	
		// exams_metadata
		def m = [:]
		def mc = ['valid': false, 'err': 'unknown']

		// def r = db.firstRow("select * from " + surveyTable + " where ACC_NUM_H='" + accNum + "'")
		def r = db.firstRow("select RESEARCH_ID, IMPLANT_EVER, IMPLANT_NOW, BC_HISTORY, YEARS_SINCE_PREVIOUS_BC, PREVIOUS_BC_LATERALITY, " +
                            "REDUX_HISTORY, REDUX_LATERALITY, HRT, ANTIESTROGEN, FIRST_DEGREE_WITH_BC, FIRST_DEGREE_WITH_BC50, BMI, RACE, " +
							"LATERALITY, BREAST_CANCER " +
                            "from " + surveyTable + " where ACC_NUM_H='" + accNum + "'")
		if(!r) {
			// System.err.println "  No record is found in survey for ACC_NUM_H=" + accNum
			mc['err'] = 'No record is found in survey.'
			return mc
		}
		// println "    r=" + r
		String research_id = r.RESEARCH_ID
		m['subjectId'] = research_id
		m['examIndex'] = 1
		m['daysSincePreviousExam'] = 0

		m['cancerL'] = '*'
		m['cancerR'] = '*'
		m['invL'] = '*'
		m['invR'] = '*'
		// r.BREAST_CANCER = NULL, Y-INV, N, Y-DCIS
		// r.LATERALITY: '', RIGHT(1), LEFT(2), BILAT SIMULT(4)
		if(r.BREAST_CANCER == 'Y-INV') {
			if(r.LATERALITY == 'RIGHT(1)') {
				m['cancerL'] = '0'
				m['cancerR'] = '1'
				m['invL'] = '0'
				m['invR'] = '1'
			}
			else if(r.LATERALITY == 'LEFT(2)') {
				m['cancerL'] = '1'
				m['cancerR'] = '0'
				m['invL'] = '1'
				m['invR'] = '0'
			}
		}
		else if(r.BREAST_CANCER == 'Y-DCIS') {
			if(r.LATERALITY == 'RIGHT(1)') {
				m['cancerL'] = '0'
				m['cancerR'] = '1'
				m['invL'] = '0'
				m['invR'] = '0'
			}
			else if(r.LATERALITY == 'LEFT(2)') {
				m['cancerL'] = '1'
				m['cancerR'] = '0'
				m['invL'] = '0'
				m['invR'] = '0'
			}
		}
		else if(r.BREAST_CANCER == 'N') {
			// println "accNum=" + accNum + " ==> BREAST_CANCER = N"
			// System.exit(0)
			m['cancerL'] = '0'
			m['cancerR'] = '0'
			m['invL'] = '0'
			m['invR'] = '0'
		}

		m['implantEver'] = r.IMPLANT_EVER
		  if(m['implantEver'] == null) m['implantEver'] = '.'
		m['implantNow'] = r.IMPLANT_NOW
		  if(m['implantNow'] == null) m['implantNow'] = '.'
		m['bcHistory'] = r.BC_HISTORY
		  if(m['bcHistory'] == null) m['bcHistory'] = 0
		m['yearsSincePreviousBc'] = r.YEARS_SINCE_PREVIOUS_BC
		  if(m['yearsSincePreviousBc'] == null) m['yearsSincePreviousBc'] = 0
		m['previousBcLaterality'] = r.PREVIOUS_BC_LATERALITY
		  if(m['previousBcLaterality'] == null) m['previousBcLaterality'] = 3
		m['reduxHistory'] = r.REDUX_HISTORY
		  if(m['reduxHistory'] == null) m['reduxHistory'] = '.'
		m['reduxLaterality'] = r.REDUX_LATERALITY
		  if(m['reduxLaterality'] == null) m['reduxLaterality'] = '.'
		m['hrt' ] = r.HRT
		  if(m['hrt'] == null) m['hrt' ] = 9
		m['antiestrogen'] = r.ANTIESTROGEN
		  if(m['antiestrogen'] == null) m['antiestrogen'] = 9
		m['firstDegreeWithBc'] = r.FIRST_DEGREE_WITH_BC
		  if(m['firstDegreeWithBc'] == null) m['firstDegreeWithBc'] = 9
		m['firstDegreeWithBc50'] = r.FIRST_DEGREE_WITH_BC50 
		  if(m['firstDegreeWithBc50'] == null) m['firstDegreeWithBc50'] = 9
		m['bmi'] = r.BMI
		  if(m['bmi'] == null) {
		  		// System.err.println "    No bmi value is found for ACC_NUM_H=" + accNum + ". The exam is skipped."
				mc['err'] = 'No bmi value is found.'
				return mc
		  }
		m['race'] = r.RACE
		if(m['race'] == null) {
			// System.err.println "    No race value is found for ACC_NUM_H=" + accNum
			mc['err'] = 'No race value is found.'
			return mc
		}
	
		def cw = [:]
		for(int i=0; i<dcmStudyTables.size(); i++) {
			def dcmtbl_prefix = dcmStudyTables[i]
			if(dcmtbl_prefix.endsWith('_study')) dcmtbl_prefix = dcmtbl_prefix.substring(0, dcmtbl_prefix.size() - 6)
			cw = get_4_mammo_images(db, accNum, dcmtbl_prefix + "_study", dcmtbl_prefix + "_series", dcmtbl_prefix + "_instance")
			if(cw && cw['valid']) {
				cw['dcm_4_images']['dcm_table'] = dcmtbl_prefix + "_study"
				// System.err.println "    Find matching dicom data in '" + dcmtbl_prefix + "_study'"
				break
			}
		}
		if(!cw) {
			// System.err.println "    Could not find dicom data. skipped."
			mc['err'] = 'Unknown return from get_4_mammo_images().'
			return mc
		}
		else {
			if(!cw['valid']) {
				mc['err'] = cw['err']
				return mc
			}
		}

		m['age'] = cw['age']
	
		mc['exams_metadata'] = m
		mc['dcm_4_images'] = cw['dcm_4_images']
		mc['acc_num_h'] = accNum

		mc['valid'] = true
		mc['err'] = ''
	
		mc
	}
	
}
