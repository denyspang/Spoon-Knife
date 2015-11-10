package com.ubs.webapps.rsrs;

import com.ubs.automation.javax.interprocess.Communication;
import com.ubs.automation.javax.utils.common.Replacements;
import com.ubs.automation.javax.utils.common.WebApplication;
import com.ubs.automation.javax.utils.common.ConnectionPool;
import com.ubs.automation.javax.xml.*;
import java.sql.SQLException;

import javax.servlet.http.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.io.*;
import java.sql.Connection;
//import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
//import java.sql.DriverManager;
//import java.sql.PreparedStatement;
import java.sql.CallableStatement;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
//import java.sql.Types;
//import java.sql.ParameterMetaData;
import oracle.jdbc.pool.*;
/*********************************************************************************************/
/* ClassName:	Reporter
/* WrittenOn:	Wednesday 14 Mar, 2007
/* Author:		Lalit Kumar, CASH OM, Singapore
/* Last modification date: Friday 03 Aug, 2007
/*********************************************************************************************/
public class Reporter extends WebApplication{
	private Report report;
	
	public String selectSQL;									
	public String selectAllSQL;									
	public String searchSQL;									
	public String insertSQL;									
	public String updateSQL;
    public String updateWithoutOutputFieldsSQL;
	public String deleteSQL;
	
	private String PERL_COMMAND; //= "/sbcimp/run/pd/perl/5.8.8/bin/perl"; 
	private String SCRIPT_PATH;
	private String SQL_PATH;
	
	static Map<String, String> COUNTRY_LIST=new TreeMap<String, String>();

	static {
		/*	
		 sb.append("<option value=\"ALL\""+( inList("ALL", countries) )+">ALL</option>"); */
		 COUNTRY_LIST.put("Europe", "EU");
	}
	
	//cache the fields
	static List<String> REPORT_FIELD_LIST=new ArrayList<String>();
	
	/**
	* default constructor
	*/
	public Reporter(){
		super();
	}
	/**
	* overloaded constructor
	*/
	@SuppressWarnings("deprecation")
	public Reporter(Configuration configuration, HttpServletRequest request, HttpServletResponse response, HttpSession session){
		super(configuration,request,response,session);
		report = new Report(configuration, request);
		//get application SQLs
		try{
			 this.PERL_COMMAND = configuration.messages.get("configuration.perl.command"); 
             this.SQL_PATH = this.request.getRealPath("/")+configuration.getApplicationSqlPath(); 
             this.SCRIPT_PATH = this.request.getRealPath("/")+configuration.getApplicationScriptPath();
             
             	this.selectSQL  = getSQL("select.sql");
				this.selectAllSQL  = getSQL("selectAll.sql");
				this.insertSQL	= getSQL("insert.sql");
				this.updateSQL	= getSQL("update.sql");
				this.deleteSQL = getSQL("delete.sql");
                this.updateWithoutOutputFieldsSQL = getSQL("updateWithoutOutputFields.sql");
               
		}catch ( Exception e){
			redirectError("Reporter::Reporter::"+e.toString());
		}
		System.gc();
	}
        
        /*
         * get Report from database
         */
        //TODO do something
        public Report getReportFromDB(int id) throws Exception{
            if(id <=0){
                throw new Exception("Report ID incorrect");
            }
            Report report = null;
            try {
	            pstmt = conn.prepareStatement(this.selectSQL);
	            pstmt.setInt(1,  id);
	            rs = pstmt.executeQuery();
	            if(rs.next()){
	                report = new Report(rs);
	            }
            } 
            finally 
            {
            	//this.close();
            }
            return report;
        }
        
	/**
	* get
	*/
	public String get(int id, String btnLabel){
		log.debug("get() method initiated!");
		String str = "";
		try{
			if ( id > 0 ){
				pstmt = conn.prepareStatement(this.selectSQL);
				pstmt.setInt(1,  id);
				rs = pstmt.executeQuery();
				if (rs.next())
				str=(generateTable(true, true, btnLabel, new Report(rs)));
			}else{
				log.debug("Generating entry screen...");
				str=(generateTable(false, true, btnLabel, new Report()));
				log.debug("done...");
			}
		}catch (Exception e){
			e.printStackTrace();
			redirectError("Reporter::get::"+ this.selectSQL + ": " + e.toString());
		}finally{
			//this.close();
		}
		return (str);
	}
	/**
	* delete
	*/
	public void delete(){
		log.debug("delete() method initiated!");
		try{
			if (this.report.getId()>0){
				//delete from db
				pstmt = conn.prepareStatement(this.deleteSQL);
				pstmt.setInt(1,this.report.getId());
				if (pstmt.executeUpdate()>=1){
					//delete from crontab
					String filename = SCRIPT_PATH + "cron_entries/" + this.report.getCrmCode().hashCode()+"."+this.report.getReportName()+"."+this.request.getParameter("Updated_by")+"."+"cronentry";
					File f = new File(filename);
					if (f.exists() == false)
						filename = SCRIPT_PATH + "cron_entries/" + this.report.getCrmCode()+"."+this.report.getReportName()+"."+this.request.getParameter("Updated_by")+"."+"cronentry";
					String cronMatchingKey = this.report.getCrmCode()+" "+this.report.getReportName();
					if (changeCronEntry(filename,cronMatchingKey,"REMOVE")==0){					
						redirectSuccess();
					}else{
						redirectError("Reporter::delete::Sorry, could not delete crontab entry!");		
					}
				}
			}else{
				redirectError();	
			}
		}catch (Exception e){
			redirectError("Reporter::delete::"+ e.toString());
		}finally{
			//this.close();
		}
	}
	/**
	* getAll
	*/
	@SuppressWarnings("unchecked")
	public String getAll(String sCRMCode, String action){
		log.debug("getAll() method initiated!");
		String str = "";
		try{
				ArrayList reports = new ArrayList();
                                if (this.conn == null)
                                    System.out.println("conn is null");
				pstmt = conn.prepareStatement(this.selectAllSQL);
				pstmt.setString(1, "%"+sCRMCode+"%");
				rs = pstmt.executeQuery();
				//if (rs.next())
				//str=(generateTable(true, true, btnLabel, new Report(rs)));
				while ( rs.next() ){
					reports.add(new Report(rs, false));
				}
				str = (generateList(reports, sCRMCode, action));
		}catch (Exception e){
                        e.printStackTrace();
			redirectError("Reporter::getAll::"+ this.selectAllSQL + ": " + e.toString());
		}finally{
			//this.close();
		}
	return (str);
	}
	/**
	* insert
	*/
	public void insert(){
		log.debug("Reporter::insert::initiated!");
		String msg = null;
		//validate
		msg = report.validate();
		if ( msg != null ){
				redirectError(msg);
				return;
		}
		//validate crm code
			String crm_code_validation_status = validateCRMCode(this.report.getCrmCode());
			if (!crm_code_validation_status.equals("0")){
				msg="The following CRM code(s) is/are invalid...<br><Br>" + crm_code_validation_status;
				redirectError(msg);
				return;
			}
		//continue adding record....
		try{
			log.debug("Executing "+this.insertSQL);
			cstmt = conn.prepareCall(this.insertSQL);
			log.debug("crmcode:"+this.report.getCrmCode());
			cstmt.setString(1, this.report.getCrmCode());
			
			log.debug("mailFROM:"+this.report.getEmailFrom());
			cstmt.setString(2, this.report.getEmailFrom());
			log.debug("mailto:"+this.report.getEmailTo());
			cstmt.setString(3, this.report.getEmailTo());
			log.debug("mailcc:"+this.report.getEmailCc());
			cstmt.setString(4, this.report.getEmailCc());
			log.debug("mailsubject:"+this.report.getEmailSubject());
			cstmt.setString(5, this.report.getEmailSubject());

			log.debug("output_fields:"+ReportTemplateUtil.encodeReportTemplateToString(this.report.getReportTemplate()));
			cstmt.setString(6, ReportTemplateUtil.encodeReportTemplateToString(this.report.getReportTemplate()));
			log.debug("country:"+this.report.getCountry());
			cstmt.setString(7, this.report.getCountry());
			log.debug("days:"+this.report.getScheduleDays());
			cstmt.setString(8, this.report.getScheduleDays());

			String schedule_type = 	this.report.getScheduleType();
			log.debug("TYPE:"+schedule_type);
			cstmt.setString(9, schedule_type);

		
			if ( schedule_type.equalsIgnoreCase("Once") ){
				log.debug("time:"+this.report.getScheduleTimeHours()+":"+this.report.getScheduleTimeMinutes());
				cstmt.setString(10, this.report.getScheduleTimeHours()+":"+this.report.getScheduleTimeMinutes());
			}else if( schedule_type.equalsIgnoreCase("OnceD") ){ //MOAPXP-3460
				log.debug("time:"+this.report.getScheduleTimeHours()+":"+this.report.getScheduleTimeMinutes());
				cstmt.setString(10, this.report.getScheduleTimeHours()+":"+this.report.getScheduleTimeMinutes());
			}
			else{
				log.debug("time:"+ this.report.getScheduleIntervalStartTimeHours()+"-"+this.report.getScheduleIntervalEndTimeHours());
				cstmt.setString(10, this.report.getScheduleIntervalStartTimeHours()+"-"+this.report.getScheduleIntervalEndTimeHours());
			}
			
			log.debug("interval:"+this.report.getScheduleInterval());
			cstmt.setInt(11, this.report.getScheduleInterval());
			log.debug("timezone:"+this.report.getScheduleTimeZone());
			cstmt.setString(12, this.report.getScheduleTimeZone());
			log.debug("reportname:"+this.report.getReportName());
			cstmt.setString(13, this.report.getReportName());
			log.debug("updatedby:"+this.report.getUpdatedBy());
			cstmt.setString(14, this.report.getUpdatedBy());
			log.debug("DMA:" + this.report.getDma());
			cstmt.setString(15, this.report.getDma());
			log.debug("IncludeAllDone:" + this.report.getIncludeAllDone());
			cstmt.setString(16,this.report.getIncludeAllDone());
			//Commented several lines below as OASIS DB will be decommissioned - MOAPEU-2094
			//log.debug("LinkReport:"+this.report.getLinkReport());
			//cstmt.setInt(17, this.report.getLinkReport());
			log.debug("LinkReport:"+"0");
			cstmt.setInt(17, 0);
			log.debug("enableMergeReport:"+this.report.getEnableMergeReport());
			cstmt.setString(18, this.report.getEnableMergeReport());
			log.debug("subAccount:"+this.report.getSubAccount());
			cstmt.setString(19, this.report.getSubAccount());

			log.debug("scpServer:"+this.report.getScpServer());
			cstmt.setString(20, this.report.getScpServer());
			log.debug("scpUsername:"+this.report.getScpUsername());
			cstmt.setString(21, this.report.getScpUsername());
			log.debug("scpTargetDir:"+this.report.getScpTargetDir());
			cstmt.setString(22, this.report.getScpTargetDir());

			log.debug("fmtExcel:"+this.report.getFmtExcel());
			cstmt.setString(23, this.report.getFmtExcel());
			log.debug("fmtCSV:"+this.report.getFmtCSV());
			cstmt.setString(24, this.report.getFmtCSV());
			log.debug("fmtHTML:"+this.report.getFmtHTML());
			cstmt.setString(25, this.report.getFmtHTML());
			log.debug("fmtTSV:"+this.report.getFmtTSV());
			cstmt.setString(26, this.report.getFmtTSV());
			
			//log.debug("oasisTminus:"+this.report.getOasisTminus());
			//cstmt.setInt(27, this.report.getOasisTminus());
			log.debug("oasisTminus:"+"0");
			cstmt.setInt(27, 0);
			//log.debug("oasisDBList:"+this.report.getOasisDBList());
			//cstmt.setString(28, this.report.getOasisDBList());
			log.debug("oasisDBList:"+"");
			cstmt.setString(28, "");
			log.debug("runPrevDayFlg:"+this.report.getRunPrevDayFlg());
			cstmt.setString(29, this.report.getRunPrevDayFlg());
			log.debug("csvPostScript:"+this.report.getCsvPostScript());
			cstmt.setInt(30, this.report.getCsvPostScript());
			log.debug("emailContent:"+this.report.getEmailContent());
			cstmt.setString(31, this.report.getEmailContent());
			//log.debug("realCisCodeFlg:"+this.report.getRealCisCodeFlg());
			//cstmt.setString(32, this.report.getRealCisCodeFlg());
			log.debug("realCisCodeFlg:"+"N");
			cstmt.setString(32, "N");
			log.debug("excelHeader:"+this.report.getExcelHeader());
			cstmt.setString(33, this.report.getExcelHeader());
			log.debug("attFilename:"+this.report.getAttFilename());
			cstmt.setString(34, this.report.getAttFilename());
			
			
			cstmt.registerOutParameter(35,java.sql.Types.INTEGER);
			cstmt.executeUpdate();

			int id = cstmt.getInt(35);
			log.debug("id: "+id);
			if ( id > 0 ){
				//setup cron entry
				String filename = null;
				filename = createCronEntryFile();
				if ( filename != null ) {
					String cronMatchingKey = this.report.getCrmCode()+" "+this.report.getReportName();
					if ( changeCronEntry(filename,cronMatchingKey,"ADD") == 0){
						redirectSuccess();
					}else{
						redirectError("Reporter::insert::Could not execute script to add the cron entry!");
					}
				}else{
					redirectError("Reporter::insert::Could not create cron entry file!");
				}
			}else{
				redirectError();
			}
		}catch ( Exception e ){
				redirectError("Reporter::insert::"+e.toString());
		}
		finally
		{
			//this.close();
		}
	}
    
	public void updateWithoutOutputFields(){
		log.debug("Reporter::updateWithoutOutputFields::initiated!");
		String msg = null;
		//validate
		msg = report.validate(false);
		if ( msg != null ){
			redirectError(msg);
			return;
		}
		//validate crm code
		String crm_code_validation_status = validateCRMCode(this.report.getCrmCode());
		if (!crm_code_validation_status.equals("0")){
			msg="The following CRM code(s) is/are invalid...<br><Br>" + crm_code_validation_status;
			redirectError(msg);
			return;
		}
		//continue adding record....
		try{
			//get the orginal crm code and report name first
			pstmt = conn.prepareStatement("select crm_code, report_name from eq_reports_configuration where id = ? ");
			pstmt.setInt(1, this.report.getId());
			ResultSet rs = pstmt.executeQuery();
			rs.next();
			String oldCrmCode = rs.getString("crm_code");
			String oldReportName = rs.getString("report_name");
			rs.close();
			pstmt.close();
			
			log.debug("Executing "+this.updateSQL);
			//cstmt = conn.prepareCall(this.updateSQL);
			pstmt = conn.prepareStatement(this.updateWithoutOutputFieldsSQL);

			log.debug("mailFROM:"+this.report.getEmailFrom());
			pstmt.setString(1, this.report.getEmailFrom());

			log.debug("mailto:"+this.report.getEmailTo());
			pstmt.setString(2, this.report.getEmailTo());

			log.debug("mailcc:"+this.report.getEmailCc());
			pstmt.setString(3, this.report.getEmailCc());

			log.debug("mailsubject:"+this.report.getEmailSubject());
			pstmt.setString(4, this.report.getEmailSubject());

			//log.debug("output_fields:"+ReportTemplateUtil.encodeReportTemplateToString(this.report.getReportTemplate()));
			//	pstmt.setString(5, ReportTemplateUtil.encodeReportTemplateToString(this.report.getReportTemplate()));

			log.debug("country:"+this.report.getCountry());
			pstmt.setString(5, this.report.getCountry());

			log.debug("days:"+this.report.getScheduleDays());
			pstmt.setString(6, this.report.getScheduleDays());

			String schedule_type = 	this.report.getScheduleType();
			log.debug("TYPE:"+schedule_type);
			pstmt.setString(7, schedule_type);


			if ( schedule_type.equalsIgnoreCase("Once") ){
				log.debug("time:"+this.report.getScheduleTimeHours()+":"+this.report.getScheduleTimeMinutes());
				pstmt.setString(8, this.report.getScheduleTimeHours()+":"+this.report.getScheduleTimeMinutes());
			}else if(schedule_type.equalsIgnoreCase("OnceD") ){ //MOAPXP-3460
				log.debug("time:"+this.report.getScheduleTimeHours()+":"+this.report.getScheduleTimeMinutes());
				pstmt.setString(8, this.report.getScheduleTimeHours()+":"+this.report.getScheduleTimeMinutes());
			}
			else{
				log.debug("time:"+ this.report.getScheduleIntervalStartTimeHours()+"-"+this.report.getScheduleIntervalEndTimeHours());
				pstmt.setString(8, this.report.getScheduleIntervalStartTimeHours()+"-"+this.report.getScheduleIntervalEndTimeHours());
			}

			log.debug("interval:"+this.report.getScheduleInterval());
			pstmt.setInt(9, this.report.getScheduleInterval());

			log.debug("timezone:"+this.report.getScheduleTimeZone());
			pstmt.setString(10, this.report.getScheduleTimeZone());

			//log.debug("reportname:"+this.report.getReportName());
			//cstmt.setString(11, this.report.getReportName());
			log.debug("updatedby:"+this.report.getUpdatedBy());
			pstmt.setString(11, this.report.getUpdatedBy());

			log.debug("DMA:" + this.report.getDma());
			pstmt.setString(12, this.report.getDma());
			log.debug("IncludeAllDone:" + this.report.getIncludeAllDone());
			pstmt.setString(13,this.report.getIncludeAllDone());
			//Commented several lines below as OASIS DB will be decommissioned - MOAPEU-2094
			//log.debug("LinkReport:"+this.report.getLinkReport());
			//pstmt.setInt(14, this.report.getLinkReport());
			log.debug("LinkReport:"+"0");
			pstmt.setInt(14, 0);
			log.debug("enableMergeReport:"+this.report.getEnableMergeReport());
			pstmt.setString(15, this.report.getEnableMergeReport());
			log.debug("SubAccount:"+this.report.getSubAccount());
			pstmt.setString(16, this.report.getSubAccount());

			log.debug("scpServer:"+this.report.getScpServer());
			pstmt.setString(17, this.report.getScpServer());
			log.debug("scpUsername:"+this.report.getScpUsername());
			pstmt.setString(18, this.report.getScpUsername());
			log.debug("scpTargetDir:"+this.report.getScpTargetDir());
			pstmt.setString(19, this.report.getScpTargetDir());

			log.debug("fmtExcel:"+this.report.getFmtExcel());
			pstmt.setString(20, this.report.getFmtExcel());
			log.debug("fmtCSV:"+this.report.getFmtCSV());
			pstmt.setString(21, this.report.getFmtCSV());
			log.debug("fmtHTML:"+this.report.getFmtHTML());
			pstmt.setString(22, this.report.getFmtHTML());
			log.debug("fmtTSV:"+this.report.getFmtTSV());
			pstmt.setString(23, this.report.getFmtTSV());

			//log.debug("oasisTminus:"+this.report.getOasisTminus());
			//pstmt.setInt(24, this.report.getOasisTminus());
			log.debug("oasisTminus:"+"0");
			pstmt.setInt(24, 0);
			//log.debug("oasisDBList:"+this.report.getOasisDBList());
			//pstmt.setString(25, this.report.getOasisDBList());
			log.debug("oasisDBList:"+"");
			pstmt.setString(25, "");
			log.debug("runPrevDayFlg:"+this.report.getRunPrevDayFlg());
			pstmt.setString(26, this.report.getRunPrevDayFlg());
			log.debug("csvPostScript:"+this.report.getCsvPostScript());
			pstmt.setInt(27, this.report.getCsvPostScript());
			log.debug("emailContent:"+this.report.getEmailContent());
			pstmt.setString(28, this.report.getEmailContent());
			//log.debug("realCisCodeFlg:"+this.report.getRealCisCodeFlg());
			//pstmt.setString(29, this.report.getRealCisCodeFlg());
			log.debug("realCisCodeFlg:"+"N");
			pstmt.setString(29, "N");
			log.debug("excelHeader:"+this.report.getExcelHeader());
			pstmt.setString(30, this.report.getExcelHeader());
			log.debug("attFilename:"+this.report.getAttFilename());
			pstmt.setString(31, this.report.getAttFilename());
			
			log.debug("crmCode:"+this.report.getCrmCode());
			pstmt.setString(32, this.report.getCrmCode());
			
			log.debug("reportName:"+this.report.getReportName());
			pstmt.setString(33, this.report.getReportName());
			
			log.debug("id:"+this.report.getId());
			pstmt.setInt(34, this.report.getId());


			//pstmt.registerOutParameter(13,java.sql.Types.INTEGER);

			//pstmt.executeUpdate();
			//int id = cstmt.getInt(13);
			//log.debug("id: "+id);

			if ( pstmt.executeUpdate() >= 1 ){
				//setup cron entry
				String filename = null;
				
				if (!oldCrmCode.equals(this.report.getCrmCode()) || !oldReportName.equals(this.report.getReportName())) {						
					//First remove existing cron entry
					filename = createCronEntryFile(oldCrmCode, oldReportName);
					if ( filename != null ) {
						String cronMatchingKey = oldCrmCode+" "+oldReportName;
						if ( changeCronEntry(filename,cronMatchingKey,"REMOVE") != 0){
							redirectError("Reporter::updateWithoutOutputFields::Could not execute script to remove the cron entry for " + oldCrmCode + " " + oldReportName + ".");
						}
					} else{
						redirectError("Reporter::updateWithoutOutputFields::Could not create cron entry file for " + oldCrmCode + " " + oldReportName + ".");
					}
				}	
				
				//Now add new cron entry
				filename = createCronEntryFile();
				if ( filename != null ) {
					String cronMatchingKey = this.report.getCrmCode()+" "+this.report.getReportName();
					if ( changeCronEntry(filename,cronMatchingKey,"ADD") == 0){
						redirectSuccess();
					}else{
						redirectError("Reporter::updateWithoutOutputFields::Could not execute script to add the cron entry!");
					}
				} else{
					redirectError("Reporter::updateWithoutOutputFields::Could not create cron entry file!");
				}
			}else{
				redirectError();
			}
		}catch ( Exception e ){
			e.printStackTrace();
			redirectError("Reporter::updateWithoutOutputFields::"+ this.updateWithoutOutputFieldsSQL + ":"+ e.toString());
		} finally 
		{
			//this.close();
		}
	}
	/**
	* update
	*/
	public void update(){
		log.debug("Reporter::update::initiated!");
		String msg = null;
		//validate
		msg = report.validate();
		if ( msg != null ){
				redirectError(msg);
				return;
		}
		//validate crm code
			String crm_code_validation_status = validateCRMCode(this.report.getCrmCode());
			if (!crm_code_validation_status.equals("0")){
				msg="The following CRM code(s) is/are invalid...<br><Br>" + crm_code_validation_status;
				redirectError(msg);
				return;
			}
		//continue adding record....
		try{
			pstmt = conn.prepareStatement("select crm_code, report_name from eq_reports_configuration where id = ? ");
			pstmt.setInt(1, this.report.getId());
			ResultSet rs = pstmt.executeQuery();
			rs.next();
			String oldCrmCode = rs.getString("crm_code");
			String oldReportName = rs.getString("report_name");
			rs.close();
			pstmt.close();
			
			log.debug("Executing "+this.updateSQL);
				//cstmt = conn.prepareCall(this.updateSQL);
				pstmt = conn.prepareStatement(this.updateSQL);

			log.debug("mailFROM:"+this.report.getEmailFrom());
			pstmt.setString(1, this.report.getEmailFrom());

			log.debug("mailto:"+this.report.getEmailTo());
			pstmt.setString(2, this.report.getEmailTo());

			log.debug("mailcc:"+this.report.getEmailCc());
			pstmt.setString(3, this.report.getEmailCc());

			log.debug("mailsubject:"+this.report.getEmailSubject());
			pstmt.setString(4, this.report.getEmailSubject());

			log.debug("output_fields:"+ReportTemplateUtil.encodeReportTemplateToString(this.report.getReportTemplate()));
				pstmt.setString(5, ReportTemplateUtil.encodeReportTemplateToString(this.report.getReportTemplate()));

			log.debug("country:"+this.report.getCountry());
				pstmt.setString(6, this.report.getCountry());

			log.debug("days:"+this.report.getScheduleDays());
				pstmt.setString(7, this.report.getScheduleDays());

			String schedule_type = 	this.report.getScheduleType();
			log.debug("TYPE:"+schedule_type);
				pstmt.setString(8, schedule_type);

		
			if ( schedule_type.equalsIgnoreCase("Once") ){
				log.debug("time:"+this.report.getScheduleTimeHours()+":"+this.report.getScheduleTimeMinutes());
					pstmt.setString(9, this.report.getScheduleTimeHours()+":"+this.report.getScheduleTimeMinutes());
			}else if( schedule_type.equalsIgnoreCase("OnceD") )//MOAPXP-3460
			{
				log.debug("time:"+this.report.getScheduleTimeHours()+":"+this.report.getScheduleTimeMinutes());
				pstmt.setString(9, this.report.getScheduleTimeHours()+":"+this.report.getScheduleTimeMinutes());
			}
			else{
				log.debug("time:"+ this.report.getScheduleIntervalStartTimeHours()+"-"+this.report.getScheduleIntervalEndTimeHours());
					pstmt.setString(9, this.report.getScheduleIntervalStartTimeHours()+"-"+this.report.getScheduleIntervalEndTimeHours());
			}
			
			log.debug("interval:"+this.report.getScheduleInterval());
				pstmt.setInt(10, this.report.getScheduleInterval());

			log.debug("timezone:"+this.report.getScheduleTimeZone());
				pstmt.setString(11, this.report.getScheduleTimeZone());

			//log.debug("reportname:"+this.report.getReportName());
			//cstmt.setString(11, this.report.getReportName());
			log.debug("updatedby:"+this.report.getUpdatedBy());
				pstmt.setString(12, this.report.getUpdatedBy());

			log.debug("DMA:" + this.report.getDma());
			pstmt.setString(13, this.report.getDma());
			log.debug("IncludeAllDone:" + this.report.getIncludeAllDone());
			pstmt.setString(14,this.report.getIncludeAllDone());
			//Commented several lines below as OASIS DB will be decommissioned - MOAPEU-2094
			//log.debug("LinkReport:"+this.report.getLinkReport());
			//pstmt.setInt(15, this.report.getLinkReport());
			log.debug("LinkReport:"+"0");
			pstmt.setInt(15, 0);
			log.debug("enableMergeReport:"+this.report.getEnableMergeReport());
			pstmt.setString(16, this.report.getEnableMergeReport());
			log.debug("subAccount:"+this.report.getSubAccount());
			pstmt.setString(17, this.report.getSubAccount());

			log.debug("scpServer:"+this.report.getScpServer());
			pstmt.setString(18, this.report.getScpServer());
			log.debug("scpUsername:"+this.report.getScpUsername());
			pstmt.setString(19, this.report.getScpUsername());
			log.debug("scpTargetDir:"+this.report.getScpTargetDir());
			pstmt.setString(20, this.report.getScpTargetDir());

			log.debug("fmtExcel:"+this.report.getFmtExcel());
			pstmt.setString(21, this.report.getFmtExcel());
			log.debug("fmtCSV:"+this.report.getFmtCSV());
			pstmt.setString(22, this.report.getFmtCSV());
			log.debug("fmtHTML:"+this.report.getFmtHTML());
			pstmt.setString(23, this.report.getFmtHTML());
			log.debug("fmtTSV:"+this.report.getFmtTSV());
			pstmt.setString(24, this.report.getFmtTSV());
			
			//log.debug("oasisTminus:"+this.report.getOasisTminus());
			//pstmt.setInt(25, this.report.getOasisTminus());
			log.debug("oasisTminus:"+"0");
			pstmt.setInt(25, 0);
			//log.debug("oasisDBList:"+this.report.getOasisDBList());
			//pstmt.setString(26, this.report.getOasisDBList());
			log.debug("oasisDBList:"+"");
			pstmt.setString(26, "");
			log.debug("runPrevDayFlg:"+this.report.getRunPrevDayFlg());
			pstmt.setString(27, this.report.getRunPrevDayFlg());
			log.debug("csvPostScript:"+this.report.getCsvPostScript());
			pstmt.setInt(28, this.report.getCsvPostScript());
			log.debug("emailContent:"+this.report.getEmailContent());
			pstmt.setString(29, this.report.getEmailContent());
			//log.debug("realCisCodeFlg:"+this.report.getRealCisCodeFlg());
			//pstmt.setString(30, this.report.getRealCisCodeFlg());
			log.debug("realCisCodeFlg:"+"N");
			pstmt.setString(30, "N");
			log.debug("excelHeader:"+this.report.getExcelHeader());
			pstmt.setString(31, this.report.getExcelHeader());
			log.debug("attFilename:"+this.report.getAttFilename());
			pstmt.setString(32, this.report.getAttFilename());
			
			log.debug("crmCode:"+this.report.getCrmCode());
			pstmt.setString(33, this.report.getCrmCode());
			
			log.debug("reportName:"+this.report.getReportName());
			pstmt.setString(34, this.report.getReportName());
			
			log.debug("id:"+this.report.getId());
				pstmt.setInt(35, this.report.getId());
								


			//pstmt.registerOutParameter(13,java.sql.Types.INTEGER);
			
			//pstmt.executeUpdate();
			//int id = cstmt.getInt(13);
			//log.debug("id: "+id);

			if ( pstmt.executeUpdate() >= 1 ){
				//setup cron entry
				String filename = null;
				
				if (!oldCrmCode.equals(this.report.getCrmCode()) || !oldReportName.equals(this.report.getReportName())) {						
					//First remove existing cron entry
					filename = createCronEntryFile(oldCrmCode, oldReportName);
					if ( filename != null ) {
						String cronMatchingKey = oldCrmCode+" "+oldReportName;
						if ( changeCronEntry(filename,cronMatchingKey,"REMOVE") != 0){
							redirectError("Reporter::updateWithoutOutputFields::Could not execute script to remove the cron entry for " + oldCrmCode + " " + oldReportName + ".");
						}
					} else{
						redirectError("Reporter::updateWithoutOutputFields::Could not create cron entry file for " + oldCrmCode + " " + oldReportName + ".");
					}
				}	
								
				//Now add new cron entry
				filename = createCronEntryFile();
				if ( filename != null ) {
					String cronMatchingKey = this.report.getCrmCode()+" "+this.report.getReportName();
					if ( changeCronEntry(filename,cronMatchingKey,"ADD") == 0){
						redirectSuccess();
					}else{
						redirectError("Reporter::update::Could not execute script to add the cron entry!");
					}
				}else{
					redirectError("Reporter::update::Could not create cron entry file!");
				}
				
			}else{
				redirectError();
			}
		}catch ( Exception e ){
				e.printStackTrace();
				redirectError("Reporter::update::"+ this.updateSQL + ":"+ e.toString());
		}
		finally
		{
			//this.close();
		}
	}
	/**
	* generateTable
	*/
	public String generateTable(boolean editMode, boolean showAction, String action, Report report){
		log.debug("generateTable() method initiated!");
		if ( report == null ) return null;
		StringBuffer sb = new StringBuffer(10240);
                
                //stores the report template in comma string format
                String selectedFields = ReportTemplateUtil.convertReportColumnsToCommaString(report.getReportTemplate());
                String selectFieldWarning = "";
                
                if(selectedFields.equals("")){
                    //report template is a complex one
                    if(editMode){
                        selectFieldWarning = "Previous Report Template is imported from Excel file, please export the excel file to edit it or select fields bellow to overwrite previous settings.";
                    }
                }
                
                //set the report source, can be from file or from selected fields
                boolean isFileReportColumnSource = true;                
                if(!editMode){
                    isFileReportColumnSource = false;
                }
                else{
                    if(selectedFields.equals("")){
                        isFileReportColumnSource = true;
                    }
                    else{
                        isFileReportColumnSource = false;
                    }
                }

		sb.append("<TABLE bgcolor=\"#d6e0eb\" width=\"95%\" BORDER=\"0\" cellpadding=\"2\" cellspacing=\"0\" align=\"center\" style=\"{border:1 solid gray;}\">");
		sb.append("<TR><TD bgcolor='#e6eaee'><H5>Setting Up New Report</H5></TD></TR>");

		sb.append("<!-- client details -->");
		sb.append("<tr><td>");
		sb.append("<table border=\"0\" cellpadding=\"3\" width=\"100%\">");
		sb.append("<TR><TD style=\"{border: 1 solid gray;}\" width=\"18%\" valign=\"top\"><H5>Client details &#187;</H5></TD><td colspan=\"4\" style=\"{border: 1 solid gray;}\">");
			sb.append("<table border=\"0\" width=\"100%\"  cellpadding=\"4\">");
			if (!editMode)
				sb.append("<tr><td>CRM Code: </td><td><INPUT TYPE=\"text\" NAME=\"Crm_code\" value=\""+report.getCrmCode()+"\" "+((editMode)? " readonly " :"")+" size=\"30\"></td>");
			else
				//sb.append("<tr><td>CRM Code: </td><td><INPUT TYPE=\"hidden\" NAME=\"Crm_code\" value=\""+report.getCrmCode()+"\"><B>"+report.getCrmCode()+"</B></td>");
				sb.append("<tr><td>CRM Code: </td><td><INPUT TYPE=\"text\" NAME=\"Crm_code\" value=\""+report.getCrmCode()+"\" size=\"30\"></td>");

			sb.append("<td>Email To: </td><td><INPUT TYPE=\"text\" NAME=\"Email_to\" value=\""+report.getEmailTo()+"\" size=\"30\"></td></tr>");
			sb.append("<tr><td>Email From: </td><td><INPUT TYPE=\"text\" NAME=\"Email_from\" value=\""+report.getEmailFrom()+"\" size=\"30\"></td>");
			sb.append("<td><I>Email Cc:</I></td><td><INPUT TYPE=\"text\" NAME=\"Email_cc\" value=\""+report.getEmailCc()+"\"  size=\"30\"><br>(optional, seperated by , or ;)</td></tr>");

			sb.append("<tr><td>Email Subject: </td><td colspan=\"3\"><INPUT TYPE=\"text\" NAME=\"Email_subject\" value=\""+escapeHTML(report.getEmailSubject())+"\" size=\"80\"></td></tr>");
			sb.append("<tr><td>Email Contents: </td><td colspan=\"3\"><TEXTAREA NAME=\"emailContent\" COLS=80 ROWS=5>"+escapeHTML(report.getEmailContent())+"</TEXTAREA></td></tr>");

			
			sb.append("</table>");
		sb.append("</td></TR>");
		sb.append("</table>");
		sb.append("</td></tr>");



		sb.append("<!-- Report details -->");
		sb.append("<tr><td>");
			sb.append("<table border=\"0\" cellpadding=\"3\" width=\"100%\">");
			sb.append("<TR><TD style=\"{border: 1 solid gray;}\" width=\"18%\" valign=\"top\"><H5>Report details &#187;</H5></TD><td colspan=\"4\" style=\"{border: 1 solid gray;}\">");
                        sb.append("<input type=\"radio\" name=\"reportColumnSource\" value=\"selectedFields\" " + (isFileReportColumnSource? "": "CHECKED") + "/>Use the fields below:");
                        sb.append("<BR/>");
                        sb.append("<FONT color=\"RED\">");
                        sb.append(selectFieldWarning);
                        sb.append("</FONT>");
			sb.append("<table align=\"left\" border=\"0\">");
					sb.append("<TR>");
						sb.append("<TD>Available Fields:(<A HREF=\"guide/field_list.html\" TARGET=\"_blank\">Fields Definition</A>)</TD>");
						sb.append("<TD>&nbsp;</TD>");
						sb.append("<TD>Selected Fields:</TD>");
						sb.append("<TD>&nbsp;</TD>");
					sb.append("</TR>");
					sb.append("<TR>");
						sb.append("<TD>");
						sb.append("<SELECT NAME=\"Available_fields\" MULTIPLE SIZE=\"8\" onDblClick=\"ot.transferRight();\" style=\"{width:200px;}\">");
							sb.append(getAvailableFields(ReportTemplateUtil.convertReportColumnsToCommaString(report.getReportTemplate())));
						sb.append("</SELECT>");
						sb.append("</TD>");
						sb.append("<TD VALIGN=\"MIDDLE\" ALIGN=\"CENTER\">");
							sb.append("<INPUT TYPE=\"button\" NAME=\"right\" VALUE=\"&gt;&gt;\" onClick=\"ot.transferRight();\"><BR><BR>");
							sb.append("<INPUT TYPE=\"button\" NAME=\"right\" VALUE=\"All &gt;&gt;\" onClick=\"ot.transferAllRight();\"><BR><BR>");
							sb.append("<INPUT TYPE=\"button\" NAME=\"left\" VALUE=\"&lt;&lt;\" onClick=\"ot.transferLeft();\"><BR><BR>");
							sb.append("<INPUT TYPE=\"button\" NAME=\"left\" VALUE=\"All &lt;&lt;\" onClick=\"ot.transferAllLeft();\">");
						sb.append("</TD>");
						sb.append("<TD>");
						sb.append("<SELECT NAME=\"Selected_fields\" MULTIPLE SIZE=\"8\" onDblClick=\"ot.transferLeft();\" style=\"{width:200px;}\">");
						
						String outfields = ReportTemplateUtil.convertReportColumnsToCommaString(report.getReportTemplate());
						if (outfields!=null && !outfields.equals("")){
							String [] aOutFields = outfields.split(",");

							if (aOutFields!=null){
								for ( String field : aOutFields){
									sb.append("<OPTION VALUE='"+field+"'>"+field+"</OPTION>");
								}
							}
						}

						sb.append("</SELECT>");
						sb.append("</TD>");
						sb.append("<TD VALIGN=\"middle\">");
							sb.append("<INPUT TYPE=\"button\" VALUE=\"&nbsp;&nbsp;Up&nbsp;&nbsp;\" onClick=\"moveOptionUp(this.form['Selected_fields'])\">");
							sb.append("<br>");
							sb.append("<INPUT TYPE=\"button\" VALUE=\"Down\" onClick=\"moveOptionDown(this.form['Selected_fields'])\">");
                                                        sb.append("<BR/><BR/>");
                                                        sb.append("<INPUT TYPE=\"submit\" VALUE=\"Export Fields to Excel\" onClick=\"setFormExportTemplateFromFields()\">");
						sb.append("</TD>");
					sb.append("</TR>");
                                        

                                        sb.append("<TR>");
                            sb.append("<TD colspan=\"3\">");
                                sb.append("<BR>");
                                sb.append("<input type=\"radio\" name=\"reportColumnSource\" value=\"file\" " + (!isFileReportColumnSource? "": "CHECKED") + "/>Import from Excel File:");
                                sb.append("<BR><BR>");
                                sb.append("<INPUT TYPE=\"file\" NAME=\"inputExcelFile\" VALUE=\"Import Template\" width=\"10\" /><BR>");
                                sb.append("<BR>");
                                sb.append("Please close the file before uploading<br>");
                                sb.append("<A HREF=\"guide/excel_guide.html\" TARGET=\"_blank\">Guide on using Excel file</A>");
                                sb.append("<BR><BR>");                                
                                sb.append("<INPUT TYPE=\"button\" VALUE=\"Export Previous Settings to Excel File\" NAME=\"\" onClick=\"exportTemplateFromServer(" + report.id + ")\"><BR>");
                                if(editMode)
                                    sb.append("<br><input type=\"radio\" name=\"reportColumnSource\" value=\"unchange\">Use existing settings");
                                
                            sb.append("</TD>");
                        sb.append("</TR>");
                        sb.append("</TABLE>");
			sb.append("</td></TR>");
                        
		sb.append("</table>");
		sb.append("</td></tr>");


		sb.append("<!-- Report parameters -->");
		sb.append("<tr><td>");
			sb.append("<table border=\"0\" cellpadding=\"3\" width=\"100%\">");
			sb.append("<TR><TD style=\"{border: 1 solid gray;}\" width=\"18%\" valign=\"top\"><H5>Report parameters &#187;</H5></TD><td colspan=\"4\" style=\"{border: 1 solid gray;}\">");
				sb.append("<table border=\"0\" cellpadding=\"2\">");
					sb.append("<tr>");
						sb.append("<td>Country: </td><td>");
								 sb.append("<select multiple name=\"Country\" size=\"2\" style=\"{width:100px;}\" "+((editMode)? " readonly " :"")+">");
								 String countries = report.getCountry();
								 for (Iterator<Entry<String, String>> i=COUNTRY_LIST.entrySet().iterator(); i.hasNext();)
								 {
									 Entry<String,String> entry = i.next();
									 sb.append("<option value=\""+ entry.getValue() +"\" "+( inList(entry.getValue(), countries) )+">"+entry.getKey()+"</option>");
								 }
								 sb.append("</select>");
						sb.append("</td>");
					sb.append("</tr>");

					sb.append("<tr>");
					sb.append("<td>DMA: </td><td>");
							 sb.append("<select name=\"DMA\" size=\"1\" style=\"{width:200px;}\" >");
							 String dma = report.getDma();
							 sb.append("<option value=\"Y\" "+( inList("Y", dma) )+">DMA only</option>");
							 sb.append("<option value=\"N\" "+( inList("N", dma) )+">non-DMA only</option>");
							 sb.append("<option value=\"A\" "+( inList("A", dma) )+">All</option>");
							 sb.append("</select>");
					sb.append("</td>");
					sb.append("</tr>");
					sb.append("<tr>");
					sb.append("<td>Include All-done Orders: </td><td>");
					 	sb.append("<input type=checkbox name=\"includeAllDone\" "+ ((report.isIncludeAllDone())? "checked" : "" ) + " value='Y'>");
 				    sb.append("</td>");
					sb.append("</tr>");
					sb.append("<tr>");
					  sb.append("<td>Sub accounts: </td><td>");
					  sb.append("<INPUT TYPE=\"text\" NAME=\"subAccount\" value=\""+report.getSubAccount()+"\" size=\"60\"></td>");
				    sb.append("</tr>");
					sb.append("<td>Run on prev day orders: </td><td>");
				 		sb.append("<input type=checkbox name=\"runPrevDayFlg\" "+ ((report.isRunPrevDayFlg())? "checked" : "" ) + " value='Y'>");
				    sb.append("</td>");
				sb.append("</table>");
			sb.append("</td></TR>");
		sb.append("</table>");
		sb.append("</td></tr>");

		
		sb.append("<!-- Merge OASIS Report -->");
		sb.append("<tr><td>");
			sb.append("<table border=\"0\" cellpadding=\"3\" width=\"100%\">");
			sb.append("<TR><TD style=\"{border: 1 solid gray;}\" width=\"18%\" valign=\"top\"><H5>Email empty report &#187;</H5></TD><td colspan=\"4\" style=\"{border: 1 solid gray;}\">");
				sb.append("<table border=\"0\" cellpadding=\"2\">");
					sb.append("<tr>");
						sb.append("<td width=\"40%\">Enable: </td><td>");
							sb.append("<input type=checkbox name=\"enableMergeReport\" "+ ((report.isEnableMergeReport())? "checked" : "" ) + " value='Y'></td>");
 						sb.append("</tr>");
					sb.append("</table>");
				sb.append("</td></TR>");
			sb.append("</table>");
		sb.append("</td></tr>");


		sb.append("<!-- Scheduling details -->");
		sb.append("<tr><td>");
			sb.append("<table border=\"0\" cellpadding=\"3\" width=\"100%\">");
			sb.append("<TR><TD style=\"{border: 1 solid gray;}\" width=\"18%\" valign=\"top\"><H5>Scheduling details &#187;</H5></TD><td colspan=\"4\" style=\"{border: 1 solid gray;}\">");
				sb.append("<table border=\"0\" width=\"100%\" cellpadding=\"4\">");
					sb.append("<tr>");
						sb.append("<td valign=\"top\">");
						sb.append("<table border=\"0\" cellpadding=\"2\" cellspacing=\"2\" width=\"100%\">");
						//sb.append("<tr><td>Run</td><td></td><td></td></tr>");
						sb.append("<tr><td align=\"left\" width=\"10%\">Run on:</td><td> ");
						String schedulingdays = report.getScheduleDays();
						log.debug("got scheduling days = "+schedulingdays);

							sb.append("<SELECT NAME=\"Days\">");
							sb.append("<OPTION VALUE=\"1-5\" "+( inList("1-5", schedulingdays) )+">Mon-Fri</OPTION>");
							sb.append("<OPTION VALUE=\"2-6\" "+( inList("2-6", schedulingdays) )+">Tue-Sat</OPTION>");
							sb.append("<OPTION VALUE=\"2-5\" "+( inList("2-5", schedulingdays) )+">Tue-Fri</OPTION>");
							sb.append("<OPTION VALUE=\"3-5\" "+( inList("3-5", schedulingdays) )+">Wed-Fri</OPTION>");
							sb.append("<OPTION VALUE=\"4-5\" "+( inList("4-5", schedulingdays) )+">Thu-Fri</OPTION>");
							sb.append("<OPTION VALUE=\"1\" "+( inList("1", schedulingdays) )+">Every Monday Only</OPTION>");
							sb.append("<OPTION VALUE=\"2\" "+( inList("2", schedulingdays) )+">Every Tuesday Only</OPTION>");
							sb.append("<OPTION VALUE=\"3\" "+( inList("3", schedulingdays) )+">Every Wednesday Only</OPTION>");
							sb.append("<OPTION VALUE=\"4\" "+( inList("4", schedulingdays) )+">Every Thursday Only</OPTION>");
							sb.append("<OPTION VALUE=\"5\" "+( inList("5", schedulingdays) )+">Every Friday Only</OPTION>");
							sb.append("<OPTION VALUE=\"6\" "+( inList("6", schedulingdays) )+">Every Saturday Only</OPTION>");
							sb.append("</SELECT>");
						sb.append("</td><td></td></tr>");
						sb.append("<tr><td align=\"right\"></td><td>");
						sb.append("<table border=\"0\" cellpadding=\"2\" cellspacing=\"2\" width=\"100%\">");
						sb.append("<tr><td align=\"left\">");
						sb.append("<input type=\"radio\" name=\"Run_type\" value=\"Once\" "+((report.getScheduleType()!=null && report.getScheduleType().equals("Once"))? " checked " : "")+"> once <br>");
						sb.append("<input type=\"radio\" name=\"Run_type\" value=\"OnceD\" "+((report.getScheduleType()!=null && report.getScheduleType().equals("OnceD"))? " checked " : "")+"> once (deferrable)");
						sb.append("</td><td valign=\"middle\" width=\"80%\"> at:&nbsp;&nbsp;");
						sb.append("<SELECT NAME=\"Hours\">");
						sb.append("<OPTION VALUE=\"NA\"></OPTION>");
							String hours = report.getScheduleTimeHours();
							for ( int h=0; h<=23; h++ ){
								String sh = (h<10) ? "0" + String.valueOf(h) : String.valueOf(h);
								sb.append("<OPTION VALUE=\""+sh+"\" "+( (hours!=null && hours.equals(sh)) ? "selected" : "")+">"+sh+"</OPTION>");
							}
						sb.append("</SELECT>HH &nbsp;:&nbsp;");
						sb.append("<SELECT NAME=\"Minutes\">");
						sb.append("<OPTION VALUE=\"NA\"></OPTION>");
							String minutes = report.getScheduleTimeMinutes();
							for ( int m=0; m<=55; m+=5 ){
								String sm = (m<10) ? "0" + String.valueOf(m) : String.valueOf(m);
								sb.append("<OPTION VALUE=\""+sm+"\" "+( (minutes!=null && minutes.equals(sm)) ? "selected" : "")+">"+sm+"</OPTION>");
							}
						sb.append("</SELECT>MM &nbsp;");
						sb.append("</td></tr></table>");
						sb.append("</td><td></td></tr>");
						sb.append("<tr><td align=\"right\"></td><td><input type=\"radio\" name=\"Run_type\" value=\"Multiple\" "+((report.getScheduleType()!=null && report.getScheduleType().equals("Multiple"))? " checked " : "")+"> every:&nbsp;&nbsp;");
						sb.append("<SELECT NAME=\"Interval\">");
						sb.append("<OPTION VALUE=\"NA\"></OPTION>");
							/*
							for ( int ih=30; ih>=0; ih-=15 ){
								String sih = (ih<10)? "0" + String.valueOf(ih) : String.valueOf(ih);
								if (ih==0)
								sb.append("<OPTION VALUE=\""+sih+"\">1 Hour</OPTION>");
								else
								sb.append("<OPTION VALUE=\""+sih+"\">"+sih+" Minutes</OPTION>");
							}*/
						int interval = report.getScheduleInterval();
						String sInterval = String.valueOf(interval);

						sb.append("<OPTION VALUE=\"15\" "+( inList("15", sInterval) )+">15 Mins</OPTION>");
						sb.append("<OPTION VALUE=\"30\" "+( inList("30", sInterval) )+">30 Mins </OPTION>");
						sb.append("<OPTION VALUE=\"0\" "+( inList("0", sInterval) )+">1 Hour</OPTION>");
						sb.append("</SELECT>&nbsp;&nbsp;from ");
						sb.append("<SELECT NAME=\"Interval_start_hours\">");
						sb.append("<OPTION VALUE=\"NA\"></OPTION>");
							String ishours = report.getScheduleIntervalStartTimeHours();
							for ( int h=0; h<=23; h++ ){
								String sh = (h<10) ? "0" + String.valueOf(h) : String.valueOf(h);
								sb.append("<OPTION VALUE=\""+sh+"\" "+( (ishours!=null && ishours.equals(sh)) ? "selected" : "")+">"+sh+"</OPTION>");
							}
						sb.append("</SELECT>HH &nbsp;&nbsp;");
						sb.append("to ");
						sb.append("<SELECT NAME=\"Interval_end_hours\">");
						sb.append("<OPTION VALUE=\"NA\"></OPTION>");
							String iehours = report.getScheduleIntervalEndTimeHours();
							for ( int h=0; h<=23; h++ ){
								String sh = (h<10) ? "0" + String.valueOf(h) : String.valueOf(h);
								sb.append("<OPTION VALUE=\""+sh+"\" "+( (iehours!=null && iehours.equals(sh)) ? "selected" : "")+">"+sh+"</OPTION>");
							}
						sb.append("</SELECT>HH ");
						sb.append("</td><td></td></tr>");
						sb.append("<tr><td align=\"left\">Time zone:</td><td colspan=\"2\">");
						sb.append(getTimeZones(report.getScheduleTimeZone()));
						sb.append("</td></tr>");
						sb.append("</table>");
					sb.append("</td>");
					sb.append("</tr>");
				sb.append("</table>");
			sb.append("</td></TR>");
		sb.append("</table>");
		sb.append("</td></tr>");

		sb.append("<!-- SCP -->");
		sb.append("<tr><td>");
			sb.append("<table border=\"0\" cellpadding=\"3\" width=\"100%\" >");
			sb.append("<TR><TD style=\"{border: 1 solid gray;}\" width=\"18%\" valign=\"top\"><H5>Secure Copy(SCP) &#187;</H5>(optional)</TD><td colspan=\"4\" style=\"{border: 1 solid gray;}\">");
				sb.append("<table border=\"0\" cellpadding=\"2\">");
					sb.append("<tr><td>Hostname: </td><td><INPUT TYPE=\"text\" NAME=\"scpServer\" value=\""+report.getScpServer()+"\"  size=\"30\" ></td>");
					sb.append("<td>Login name: </td><td><INPUT TYPE=\"text\" NAME=\"scpUsername\" value=\""+report.getScpUsername()+"\"  size=\"30\" ></td></tr>");
					sb.append("<tr><td>Destination Directory: </td><td colspan=\"3\"><INPUT TYPE=\"text\" NAME=\"scpTargetDir\" value=\""+report.getScpTargetDir()+"\"  size=\"70\" ></td></tr>");
				sb.append("</table>");
			sb.append("</td></TR>");
			sb.append("</table>");
		sb.append("</td></tr>");

		sb.append("<!-- File Format -->");
		sb.append("<tr><td>");
			sb.append("<table border=\"0\" cellpadding=\"3\" width=\"100%\" >");
			sb.append("<TR><TD style=\"{border: 1 solid gray;}\" width=\"18%\" valign=\"top\"><H5>Report Format &#187;</H5></TD><td colspan=\"4\" style=\"{border: 1 solid gray;}\">");
				sb.append("<table border=\"0\" cellpadding=\"2\">");
				sb.append("<tr><td>Report Filename: </td><td><INPUT TYPE=\"text\" NAME=\"attFilename\" value=\""+report.getAttFilename()+"\"  size=\"40\" >(<A HREF=\"guide/filename.html\" TARGET=\"_blank\">Magic Code for Filename</A>)</td>");
 			    sb.append("<tr><td>CSV: </td><td>");
			    sb.append("<input type=checkbox name=\"fmtCSV\" "+ ((report.isFmtCSV())? "checked" : "" ) + " value='Y'>");
			    sb.append(" Post-processing script:");
			    sb.append(genCSVPostScriptList(report.getCsvPostScript(), editMode));
			    sb.append("</td></tr>");
			    sb.append("<tr><td>HTML: </td><td>");
 			    sb.append("<input type=checkbox name=\"fmtHTML\" "+ ((report.isFmtHTML())? "checked" : "" ) + " value='Y'></td></tr>");
 			    sb.append("<tr><td>Tab seperated CSV: </td><td>");
			    sb.append("<input type=checkbox name=\"fmtTSV\" "+ ((report.isFmtTSV())? "checked" : "" ) + " value='Y'></td></tr>");
			    sb.append("<tr><td>Excel: </td><td>");
 			    sb.append("<input type=checkbox name=\"fmtExcel\" "+ ((report.isFmtExcel())? "checked" : "" ) + " value='Y'></td></tr>");
			    sb.append("<tr><td>Excel Header (optional): </td><td colspan=\"3\"><TEXTAREA NAME=\"excelHeader\" COLS=80 ROWS=5>"+escapeHTML(report.getExcelHeader())+"</TEXTAREA></td></tr>");
			    sb.append("</table>");
			sb.append("</td></TR>");
			sb.append("</table>");
		sb.append("</td></tr>");
		
		sb.append("<!-- MODEL name -->");
		sb.append("<tr><td>");
			sb.append("<table border=\"0\" cellpadding=\"3\" width=\"100%\" >");
			sb.append("<TR><TD style=\"{border: 1 solid gray;}\" width=\"18%\" valign=\"top\"><H5>Save configuration &#187;</H5></TD><td colspan=\"4\" style=\"{border: 1 solid gray;}\">");
				sb.append("<table border=\"0\" cellpadding=\"8\">");
				if (!editMode)
					sb.append("<tr><td>Report name: </td><td><INPUT TYPE=\"text\" NAME=\"Report_name\" value=\""+report.getReportName()+"\"  size=\"40\" "+((editMode) ? " readonly " :"")+"></td>");
				else
					//sb.append("<tr><td>Report name: </td><td><INPUT TYPE=\"hidden\" NAME=\"Report_name\" value=\""+report.getReportName()+"\"  size=\"40\"><B>"+report.getReportName()+"</B></td>");
					sb.append("<tr><td>Report name: </td><td><INPUT TYPE=\"text\" NAME=\"Report_name\" value=\""+report.getReportName()+"\"  size=\"40\" ></td>");

				sb.append("</tr>");
				sb.append("</table>");
			sb.append("</td></TR>");
			sb.append("</table>");
		sb.append("</td></tr>");
	
		sb.append("<TR><TD ALIGN=\"RIGHT\"><INPUT TYPE=\"hidden\" name=\"Id\" value=\""+report.getId()+"\"><INPUT TYPE=\"hidden\" name=\"txtSelected_fields\" value=\""+ReportTemplateUtil.convertReportColumnsToCommaString(report.getReportTemplate())+"\"><INPUT TYPE=\"Reset\" Value=\"Reset\" onClick=\"this.form.reset(); resetDynamicOptionLists(this.form);\">&nbsp;&nbsp;<INPUT TYPE=\"SUBMIT\" VALUE=\""+action+"\" onClick=\"setFormSubmit()\"></TD></TR>");
		sb.append("</TABLE>");
	
		return (sb.toString());
	}
	/**
	* getAll
	*/
	public String generateList(ArrayList data, String sCRMCode, String action){
		if (data==null) return null;
		StringBuffer sb = new StringBuffer();
		sb.append("<table border=0 cellpadding=2 cellspacing=2 width='100%'>");
		//PRINT HEADING ROW
		sb.append("<tr bgcolor='#d6e0eb'>");
		sb.append("<td class=bbTD width='1%'>#</td>");
		sb.append("<td class=bbTD width='3%'>Report Name</td>");
		sb.append("<td class=bbTD width='3%'>CRM Code</td>");
		sb.append("<td class=bbTD width='3%'>Country</td>");
		sb.append("<td class=bbTD width='3%'>Schedule type</td>");
		sb.append("<td class=bbTD width='3%'>Schedule days</td>");
		sb.append("<td class=bbTD width='3%'>Schedule time</td>");
		sb.append("<td class=bbTD width='3%'>Options</td>");

		sb.append("</tr>");
		//Now print data
		for ( int k=0; k<data.size(); k++){
			Report report = (Report) data.get(k);
			int ID				= report.getId();
			String CRM_CODE		= report.getCrmCode();
			String NAME			= report.getReportName();
			String COUNTRY		= report.getCountry();
			String SCHEDULE_TYPE= report.getScheduleType();
			String SCHEDULE_DAYS= report.getScheduleDays();
			String SCHEDULE_TIME= "";
			if (SCHEDULE_TYPE.equalsIgnoreCase("ONCE")){
				SCHEDULE_TIME = report.getScheduleTimeHours() + ":" + report.getScheduleTimeMinutes();
			}else if(SCHEDULE_TYPE.equalsIgnoreCase("ONCED")){ //MOAPXP-3460
				SCHEDULE_TIME = report.getScheduleTimeHours() + ":" + report.getScheduleTimeMinutes();
			}
			else{
				SCHEDULE_TIME = report.getScheduleIntervalStartTimeHours() + "-" + report.getScheduleIntervalEndTimeHours() + "hrs";
			}
			
			String SCHEDULE_TIMEZONE= report.getScheduleTimeZone();


			@SuppressWarnings("unused")
			String UPDATEDON	= report.getUpdatedOn();
			String UPDATEDBY	= report.getUpdatedBy();
			sb.append("<tr>");
			//sb.append("<td valign=top>"+ID+"</td>");
			sb.append("<td valign=top>"+(k+1)+"</td>");
			sb.append("<td NOWRAP valign=top>"+NAME+"</td>");
			sb.append("<td valign=top NOWRAP>"+CRM_CODE+"</td>");
			sb.append("<td valign=top NOWRAP>"+COUNTRY+"</td>");
			sb.append("<td valign=top NOWRAP>"+SCHEDULE_TYPE+"</td>");
			sb.append("<td valign=top NOWRAP>"+SCHEDULE_DAYS+"</td>");
			sb.append("<td valign=top NOWRAP>"+SCHEDULE_TIME+" "+SCHEDULE_TIMEZONE+"</td>");

			if ( action.equalsIgnoreCase("change") ){
				sb.append("<td valign=top NOWRAP><a href='Display.jsp?Id="+ID+"'>Edit</a> | ");
				sb.append("<a onclick=\"return confirmDelete();\" href='Delete.jsp?Id="+ID+"&Crm_code="+CRM_CODE+"&Report_name="+NAME+"&Updated_by="+UPDATEDBY+"'>Delete</a></td>");
			}else if (action.equalsIgnoreCase("rerun")){
				sb.append("<td valign=top NOWRAP><INPUT TYPE=\"button\" onclick=\"window.location.href='Run.jsp?Id="+ID+"';\" value=\"Re-run\"></td>");
			}
			sb.append("</tr>");
		}
		sb.append("</table>");
	return sb.toString();
	}
	/**
	*  getTimeZones
	*  Returns list of Time zones.
	*/
	@SuppressWarnings("finally")
	public String getTimeZones(String tz){
		StringBuffer sb= new StringBuffer();
		String sql = "SELECT ABBREVIATION, OFFSET, TEXT FROM EQ_TIME_ZONES ORDER BY ABBREVIATION";
		try{
			//stmt = conn.createStatement();
                        //TODO: remove the following comment
			//rs = stmt.executeQuery(sql);
			sb.append("<SELECT NAME='Time_zone'>");
			sb.append("<OPTION VALUE=\"NA\"></OPTION>");
			sb.append("<OPTION VALUE=\"GMT\"  "+( inList("GMT", tz) )+">(UTC+00:00) GMT - Greenwich Mean Time</OPTION>");
			sb.append("</SELECT>");
		}catch (Exception e){
			redirectError("Reporter::getTimeZones::"+e.toString());
		}finally{
			//close();
			return (sb.toString());
		}
	}
		/**
	*  getTimeZones2
	*  Returns list of Time zones.
	*/
	@SuppressWarnings("finally")
	public String getTimeZones2(String tz){
		StringBuffer sb= new StringBuffer();
		String sql = "SELECT ABBREVIATION, OFFSET, TEXT FROM EQ_TIME_ZONES ORDER BY ABBREVIATION";
		try{
			//stmt = conn.createStatement();
			rs = stmt.executeQuery(sql);
			sb.append("<SELECT NAME='Time_zone'>");
			sb.append("<OPTION VALUE=\"NA\"></OPTION>");
			while (rs.next()){
				sb.append("<OPTION VALUE=\""+rs.getString(1)+"\" "+((tz!=null && tz.equals(rs.getString(1))) ? " selected " : "" )+">("+rs.getString(2)+") "+rs.getString(1)+" - "+ rs.getString(3)+"</OPTION>");
			}
			sb.append("</SELECT>");
		}catch (Exception e){
			redirectError("Reporter::getTimeZones::"+e.toString());
		}finally{
			//this.close();
			return (sb.toString());
		}
	}
	
	
	/**
	* Returns list of all fields name for report
	*/
	@SuppressWarnings("finally")
	public String getAvailableFields(String sSelectedFields){
		StringBuffer sb=new StringBuffer();
		synchronized (REPORT_FIELD_LIST) {
			if (REPORT_FIELD_LIST.isEmpty())
			{
				//get all columns name by executing SP
				try{
					//ConnectionPool cp = new ConnectionPool("com.sybase.jdbc2.jdbc.SybDriver","jdbc:sybase:Tds:shkg0296dsy.hkg:9998/hkmoap","hkmoap_dbo","hkmoap_dbo",5,20,false);
					String _dbType=configuration.messages.get("configuration.databases.reportdata.type");
					String _dbServer=configuration.messages.get("configuration.databases.reportdata.server");
					String _dbUser=configuration.messages.get("configuration.databases.reportdata.user");
					String _dbPassword=configuration.messages.get("configuration.databases.reportdata.password");
					ConnectionPool cp = null;
					if ( _dbType.equalsIgnoreCase("sybase")){
						cp = new ConnectionPool("com.sybase.jdbc2.jdbc.SybDriver",_dbServer,_dbUser,_dbPassword,5,20,false);
					}else if (_dbType.equalsIgnoreCase("oracle")){
						OracleConnectionPoolDataSource ocpds = new OracleConnectionPoolDataSource();
		                ocpds.setURL("jdbc:oracle:thin:@"+_dbServer);
		                ocpds.setUser(_dbUser);
		                ocpds.setPassword(_dbPassword);
		                cp = (ConnectionPool) ocpds.getPooledConnection();
					}
					Connection _dataconn = cp.getConnection();
					String query = "{CALL GenerateNOEReport(?,?,?,?,?,?)}";
					CallableStatement _datestmt = _dataconn.prepareCall(query);
					_datestmt.setString(1, "2007/04/01");
					_datestmt.setString(2, "2007/04/01");
					_datestmt.setString(3, "");
					_datestmt.setString(4, "");
					_datestmt.setString(5, "");
					_datestmt.setString(6, "");
				    ResultSet _datars = _datestmt.executeQuery ();
					ResultSetMetaData _datarsmd = _datars.getMetaData();
					/*while ( rs.next()){
						System.out.println(rs.getString(1));
					}*/
					for ( int i=1; i<=_datarsmd.getColumnCount();i++){
						REPORT_FIELD_LIST.add(_datarsmd.getColumnName(i));
					}
					_datars.close();
					_datestmt.close();
					cp.free(_dataconn);
					_dataconn=null;
					
					Collections.sort(REPORT_FIELD_LIST,
							(new Comparator<String>(){

								public int compare(String arg0, String arg1) {
									return arg0.compareToIgnoreCase(arg1);
								}}));
				}catch (Exception e){
					redirectError("Reporter::getAvailableFields::"+e.toString());
					//e.printStackTrace();
				}finally{
					//return (sb.toString());
				}
			}
		}
		if (sSelectedFields==null)
			sSelectedFields="";
		String[] selectedFieldArray = sSelectedFields.split(",");
		Set<String> selectedFieldSet = new HashSet<String>();
		for (String selectedField: selectedFieldArray)
			selectedFieldSet.add(selectedField);

		for ( Iterator<String> i = REPORT_FIELD_LIST.iterator(); i.hasNext(); ){
			String currentField = i.next();
			if (!selectedFieldSet.contains(currentField)){
				sb.append("<OPTION VALUE=\""+currentField+"\">"+currentField+"</OPTION>");
			}
		}
		return sb.toString();
	}
	
	
	
	
	/**
	* getSQL
	*/
	@SuppressWarnings("deprecation")
	public String getSQL(String sql){
		//return getContents(new File(configuration.getApplicationSqlPath()+sql),this.log);
		return getContents(new File(SQL_PATH+sql),this.log);
	}
	/**
	* validateCode
	*/
	@SuppressWarnings("deprecation")
	public String validateCRMCode (String code){
                        //TODO: uncommet and don't return now
                        return "0";/*
			int status = 0;
			String strData="";
			Communication obj = new Communication(configuration.USER_ID);
			ArrayList data = new ArrayList();
			String [] params = {"CRM_CODE", "\"'" + code + "'\""};
			status = obj.executeReader(configuration.DBPARAMS, data, PERL_COMMAND, SCRIPT_PATH, "check_crm_asset_code.pl", params);
			if (status == 0 || status == -1){
				for ( int i=0; i<data.size(); i++){
					strData += data.get(i).toString();
				}
			}
		return strData;*/
	}
	
		
	public String createCronEntryFile(){
		return createCronEntryFile(this.report.getCrmCode(), this.report.getReportName());
	}
	
	/**
	* createCronEntryFile
	*/
	@SuppressWarnings("deprecation")
	public String createCronEntryFile(String crmCode, String reportName){
		java.util.Date dToday = new java.util.Date();
		@SuppressWarnings("unused")
		String sCurrentTime = String.valueOf(dToday.getHours()) +"."+ String.valueOf(dToday.getMinutes()) +"."+ String.valueOf(dToday.getSeconds());
		String crm_code  = crmCode;
		@SuppressWarnings("unused")
		String country = this.report.getCountry().replaceAll(",","_");
		String name = reportName.replaceAll(" ","_");
		//String scripts_path = this.request.getRealPath("/") + configuration.getApplicationScriptPath() ;
		
		//String filename = scripts_path + "cron_entries/" + crm_code + "." + country + "."+configuration.USER_ID+"."+Dates.getDate(dToday, Dates.FORMAT.YYYYMMDD)+"."+sCurrentTime+".cronentry";
		//String filename = scripts_path + "cron_entries/" + crm_code + "." + name + "."+configuration.USER_ID+"."+Dates.getDate(dToday, Dates.FORMAT.YYYYMMDD)+"."+sCurrentTime+".cronentry";
		String filename = SCRIPT_PATH + "cron_entries/" + crm_code.hashCode() + "." + name + "."+configuration.USER_ID+"."+"cronentry";
		log.debug("Cron entry file : " + filename);
		try {
			

			//adjust report time based on time zone selection
			calculateReportTime();

			//prepare data
			String data = "";
			
			//if schedule for single run
			if (this.report.getScheduleType().equalsIgnoreCase("Once")){
				//add minutes
				data += this.report.getScheduleTimeMinutes() + " ";
				//add hour
				data += this.report.getScheduleTimeHours() + " ";
			}else if(this.report.getScheduleType().equalsIgnoreCase("OnceD")){ //MOAPXP-3460
				//add minutes
				data += this.report.getScheduleTimeMinutes() + " ";
				//add hour
				data += this.report.getScheduleTimeHours() + " ";
			}else{ // for multiple run
				int iInterval = this.report.getScheduleInterval();
				//add minutes
				if ( iInterval==0 ){
					data += "00 ";
				}else if (iInterval==15){
					data += "00,15,30,45 ";
				}else if (iInterval==30){
					data += "00,30 ";
				}
			
				//prepare hours list	
				int iStartHours = Integer.parseInt(this.report.getScheduleIntervalStartTimeHours());
				int iEndHours = Integer.parseInt(this.report.getScheduleIntervalEndTimeHours());
				
				String sTimeField ="";
					/*for ( int i=iStartHours; i<=iEndHours; i+=iInterval){
						sTimeField+= i + ",";
					}
					sTimeField=sTimeField.substring(0,sTimeField.length()-1);
					*/
					sTimeField = String.valueOf(iStartHours)+ "-" + String.valueOf(iEndHours);

				//add hour
				data += sTimeField + " ";
			}
			//* for day
			data += "*" + " ";
			//* for month
			data += "*" + " ";
			// add weekdays, putting default to mon to fri i.e. 1-5
			data += this.report.getScheduleDays() + " ";
			//add command
			//data += "/sbcimp/run/pd/perl/5.8.8/bin/perl " + scripts_path  + "rptRunner.pl " + crm_code + " " + country;
			data += PERL_COMMAND +" " + SCRIPT_PATH  + "rptRunner.pl " + crm_code + " " + name;
			data += " >> " + SCRIPT_PATH + "rptRunner.log 2>&1\n";
			log.debug("cron entry data : " + data);
			BufferedWriter out = new BufferedWriter(new FileWriter(filename));
			out.write("### EOD Report entry from REPORTS Automation Interface on "+new java.util.Date().toString()+"\n");
			out.write(data);
			out.close();
			return filename;
	    } catch (IOException e) {
			redirectError("Reporter::createCronEntryFile::"+e.toString());
			log.error("filename:" + filename + ", SCRIPT_PATH" + SCRIPT_PATH);
			log.error("PERL_COMMAND:" + PERL_COMMAND);
			log.error("changeCronEntry",e);
		}
	return filename;
	}
	/**
	* addCronEntry
	* Executes updateCron.pl and add the entry into crontab file
	*/
	@SuppressWarnings("deprecation")
	public int changeCronEntry(String filename, String cronMatchingKey, String doWhat){
		int status=0;
		//String scripts_path = this.request.getRealPath("/") + configuration.getApplicationScriptPath();
		try{
			Communication obj = new Communication(configuration.USER_ID);
			String [] params = {filename, SCRIPT_PATH, configuration.USER_ID, doWhat, cronMatchingKey};
			status = obj.execute( PERL_COMMAND, SCRIPT_PATH, "updateCron.pl", params);
			log.debug("updateCron.pl " + params + " return status is----->>>>>> : " + status);
			return status;
			}catch(Exception e){
				redirectError("Reporter::changeCronEntry::"+e.toString());
				log.error("filename:" + filename + ", SCRIPT_PATH" + SCRIPT_PATH);
				log.error("PERL_COMMAND:" + PERL_COMMAND);
				log.error("changeCronEntry",e);
				
			}
		return status;
	}
	/**
	* checks matching for CSV item
	*/
	
	public boolean inCSVList(String what, String list)
	{
		if (list == null)
			return false;
			if (list.indexOf(",")>-1){//multiple items in list
				String items [] = list.split(",");
				for ( String item : items ){
					if (item.equalsIgnoreCase(what)){
						return true;
					}
				}
			}else{//single item in list
				if ( list.equals(what) ){
					return true;
				}
			}
			return false;
		
	}
	public String inList(String what, String list){
		if (list == null)
		return "";
		//log.debug("Reporter::inList::what="+what+",list="+list);
		if (list.indexOf(",")>-1){//multiple items in list
			String items [] = list.split(",");
			for ( String item : items ){
				if (item.equalsIgnoreCase(what)){
					//log.debug("got matched...returning [selected]");
					return " selected ";
				}
			}
		}else{//single item in list
			if ( list.equals(what) ){
				//log.debug("got matched...returning [selected]");
				return " selected ";
			}
		}
		return "";
	}
	/**
	* Run
	*/
	@SuppressWarnings("deprecation")
	public void Run(int id, String Date_from, String Date_to){
		try{
			if ( id > 0 ){
				pstmt = conn.prepareStatement(this.selectSQL);
				pstmt.setInt(1,id);
				rs = pstmt.executeQuery();
				if (rs.next())
				report = new Report(rs);
				//String scripts_path = this.request.getRealPath("/")+configuration.getApplicationScriptPath();
				Communication obj = new Communication(configuration.USER_ID);
				//String [] params = {this.report.getCrmCode(), this.report.getReportName()};
				String [] params = {this.report.getCrmCode(), this.report.getReportName(), Date_from, Date_to};
				obj.setExecuteWait(Communication.EXECUTE_NO_WAIT); 
				//Communication.EXECUTE_NO_WAIT
				int status = obj.execute( PERL_COMMAND, SCRIPT_PATH, "rptRunnerWrap.pl", params);
				if ( status == 0 ){
					redirectSuccess();
				}else{
					redirectError();
				}
			}
		}catch (Exception e){
                        e.printStackTrace();
			redirectError("Reporter::Run::"+ e.toString());
		}finally{
			//this.close();
		}
	}
	/**
	* calculateReportTime
	*/
	public void calculateReportTime(){
		log.debug("Reporter::calculateReportTime::initiated!");
		try{
		String sScheduleType = this.report.getScheduleType();
		String sSelectedTimeZone = this.report.getScheduleTimeZone();
		int iSelectedTimeZoneOffsetValue = getTimeZoneOffsetValue(sSelectedTimeZone);
		log.debug("Got iSelectedTimeZoneOffsetValue="+iSelectedTimeZoneOffsetValue);
		//int iJSTTimeZoneOffsetValue=9;
		int iJSTTimeZoneOffsetValue=Integer.parseInt(configuration.messages.get("configuration.timezone.diff.fromgmt")); //changed as it is running on HK box now
		int iTimeDifferenceInHours=0;
		//perform calculation based on JST as crontab is running on tokyo box.
		if ( iSelectedTimeZoneOffsetValue < iJSTTimeZoneOffsetValue ){
			log.debug("iSelectedTimeZoneOffsetValue is less than JST");
			iTimeDifferenceInHours = iJSTTimeZoneOffsetValue - iSelectedTimeZoneOffsetValue;
			if ( sScheduleType.equalsIgnoreCase("Once") ) {
				int t = Integer.parseInt(this.report.getScheduleTimeHours()) + iTimeDifferenceInHours;
				if ( t > 23 ) {t=t-24;}
				this.report.setScheduleTimeHours(String.valueOf(t));
			}else if (sScheduleType.equalsIgnoreCase("Multiple")){
				int t1 = Integer.parseInt(this.report.getScheduleIntervalStartTimeHours()) + iTimeDifferenceInHours;
				int t2 = Integer.parseInt(this.report.getScheduleIntervalEndTimeHours()) + iTimeDifferenceInHours;
					if ( t1 > 23 ) {t1=t1-24;}
					if ( t2 > 23 ) {t2=t2-24;}
				this.report.setScheduleIntervalStartTimeHours(String.valueOf(t1));
				this.report.setScheduleIntervalEndTimeHours(String.valueOf(t2));
			}else if(sScheduleType.equalsIgnoreCase("OnceD")){ //MOAPXP-3460
				int t = Integer.parseInt(this.report.getScheduleTimeHours()) + iTimeDifferenceInHours;
				if ( t > 23 ) {t=t-24;}
				this.report.setScheduleTimeHours(String.valueOf(t));
			}
		}else if (iSelectedTimeZoneOffsetValue > iJSTTimeZoneOffsetValue ){
			log.debug("iSelectedTimeZoneOffsetValue is more than JST");
			iTimeDifferenceInHours = iSelectedTimeZoneOffsetValue - iJSTTimeZoneOffsetValue;
			if ( sScheduleType.equalsIgnoreCase("Once") ) {
				int t = Integer.parseInt(this.report.getScheduleTimeHours()) - iTimeDifferenceInHours;
				if ( t > 23 ) {t=t-24;}
				this.report.setScheduleTimeHours(String.valueOf(t));
			}else if (sScheduleType.equalsIgnoreCase("Multiple")){
				int t1 = Integer.parseInt(this.report.getScheduleIntervalStartTimeHours()) - iTimeDifferenceInHours;
				int t2 = Integer.parseInt(this.report.getScheduleIntervalEndTimeHours()) - iTimeDifferenceInHours;
					if ( t1 > 23 ) {t1=t1-24;}
					if ( t2 > 23 ) {t2=t2-24;}
				this.report.setScheduleIntervalStartTimeHours(String.valueOf(t1));
				this.report.setScheduleIntervalEndTimeHours(String.valueOf(t2));
			}else if(sScheduleType.equalsIgnoreCase("OnceD")){ //MOAPXP-3460
				int t = Integer.parseInt(this.report.getScheduleTimeHours()) - iTimeDifferenceInHours;
				if ( t > 23 ) {t=t-24;}
				this.report.setScheduleTimeHours(String.valueOf(t));
			}
		}
		log.debug("Report time calculations has been done");
		}catch(Exception e){
			redirectError("Reporter::calculateReportTime::"+e.toString());
		}

	}
	/**
	*getTimeZoneOffsetValue
	*/
	@SuppressWarnings("finally")
	public int getTimeZoneOffsetValue(String sTimeZone){
		log.debug("Reporter::getTimeZoneOffsetValue::initiated!");
		int iOffsetValue=0;
		String sql = "SELECT OFFSET_VALUE FROM EQ_TIME_ZONES WHERE ABBREVIATION='"+sTimeZone+"'";
		try{
			rs = stmt.executeQuery(sql);
			if (rs.next()){
				iOffsetValue=rs.getInt(1);
			}
		}catch (Exception e){
			redirectError("Reporter::getTimeZoneOffsetValue::"+e.toString());
		}finally{
			return (iOffsetValue);
		}
	}
	
	protected String genOasisReportList(int reportID, boolean editMode)
	{
		StringBuffer sb = new StringBuffer();
		Map<String, Integer> repList = getOasisReportList();
		sb.append("<select name=\"linkReport\" size=\"1\" style=\"{width:200px;}\" "+((editMode)? " readonly " :"")+">");
		for (Iterator<Entry<String, Integer>> i = repList.entrySet().iterator(); i.hasNext(); )
		{
			Entry<String, Integer> entry = i.next();
			sb.append("<option value=\"")
			  .append(entry.getValue()+"")
			  .append("\"");
			if (entry.getValue() == reportID)
			{
				sb.append(" selected ");
			}
			sb.append(">");
			sb.append(entry.getKey());
			sb.append("</option>");
		}
		sb.append("</select>");
		return sb.toString();
	}
	
	public Map<String, Integer> getOasisReportList()
	{
		Map<String, Integer>  results = new TreeMap<String, Integer>();
		results.put("", 0);
		log.debug("get a list of OASIS report");
		String sql = "SELECT ID, DISPLAY_NAME FROM EQ_OASIS_REPORTS";
		try {
			rs = stmt.executeQuery(sql);
			while (rs.next())
			{
				results.put(rs.getString(2), rs.getInt(1));
			}
		}
		catch (Exception e){
			redirectError("Reporter::getOasisReportList::"+e.toString());
			log.error("getOasisReportList", e);
		}
		return results;
	}

	protected String genCSVPostScriptList(int reportID, boolean editMode)
	{
		StringBuffer sb = new StringBuffer();
		Map<String, Integer> repList = getCSVPostScriptList();
		sb.append("<select name=\"csvPostScript\" size=\"1\" style=\"{width:200px;}\" >");
		for (Iterator<Entry<String, Integer>> i = repList.entrySet().iterator(); i.hasNext(); )
		{
			Entry<String, Integer> entry = i.next();
			sb.append("<option value=\"")
			  .append(entry.getValue()+"")
			  .append("\"");
			if (entry.getValue() == reportID)
			{
				sb.append(" selected ");
			}
			sb.append(">");
			sb.append(entry.getKey());
			sb.append("</option>");
		}
		sb.append("</select>");
		return sb.toString();
	}
	
	public Map<String, Integer> getCSVPostScriptList()
	{
		Map<String, Integer>  results = new TreeMap<String, Integer>();
		results.put("", 0);
		log.debug("get a list of CSV Post processing script");
		String sql = "SELECT ID, DISPLAY_NAME FROM EQ_CSV_POST_SCRIPT";
		try {
			rs = stmt.executeQuery(sql);
			while (rs.next())
			{
				results.put(rs.getString(2), rs.getInt(1));
			}
		}
		catch (Exception e){
			redirectError("Reporter::getCSVPostScriptList::"+e.toString());
			log.error("getCSVPostScriptList", e);
		}
		return results;
	}

	
    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }
    
    /**
     * Escape characters in the same way as the <c:out> tag in JSTL.
     * 
     * <P>The following characters are replaced with character entities : < > " ' &
     */
     public static String escapeHTML(String aText){
       final StringBuilder result = new StringBuilder();
       final StringCharacterIterator iterator = new StringCharacterIterator(aText);
       char character =  iterator.current();
       while (character != CharacterIterator.DONE ){
         if (character == '<') {
           result.append("&lt;");
         }
         else if (character == '>') {
           result.append("&gt;");
         }
         else if (character == '\"') {
           result.append("&quot;");
         }
         else if (character == '\'') {
           result.append("&#039;");
         }
         else if (character == '&') {
            result.append("&amp;");
         }
         else {
           //the char is not a special one
           //add it to the result as is
           result.append(character);
         }
         character = iterator.next();
       }
       return result.toString();
     }

}
