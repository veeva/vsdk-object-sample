package com.veeva.vault.custom.triggers.workflows;

import com.veeva.vault.custom.udc.UserDefinedUtils;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.ReadRecordsResponse;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordEvent;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.data.RecordTrigger;
import com.veeva.vault.sdk.api.data.RecordTriggerContext;
import com.veeva.vault.sdk.api.data.RecordTriggerInfo;
import com.veeva.vault.sdk.api.job.JobParameters;
import com.veeva.vault.sdk.api.job.JobService;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * This trigger starts a workflow when a new vSDK Country Brand record is created. 
 * 
 * 		#1 Initiate the "Evaluate Country Brand" user action to start the "vSDK Country Brand Penetration" workflow.
 * 		#2 The workflow changes the state of the Country Brand record to "Active" or "Not Applicable".
 * 		#3 When the workflow is complete, the AFTER_UPDATE trigger updates the associated vSDK Product record via object user actions: 
 * 			- If a Country Brand become "Active", then a "Pending" parent Product becomes "In Use" with the "Change State to In Use" user action. 
 *			- If all Country Brand records become "Terminated" or "Not Applicable", then an "In Use" parent Product becomes "Off Market" with the "Change State to Off Market" user action.
 *
 */
@RecordTriggerInfo(object = "vsdk_country_brand__c", name="vsdk_country_brand_lc_user_actions__c", events = {RecordEvent.AFTER_INSERT, RecordEvent.AFTER_UPDATE})
public class CountryBrandLifeCycleUserActions implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

        RecordEvent recordEvent = recordTriggerContext.getRecordEvent();

        //Get an instance of Job for invoking user actions, such as changing state and starting workflow
        JobService jobService = ServiceLocator.locate(JobService.class);
        JobParameters jobParameters = jobService.newJobParameters("record_user_action__v");
        
    	if (UserDefinedUtils.example_type.equals(UserDefinedUtils.ALL)){

	        if (recordEvent.toString().equals("AFTER_INSERT")) {
	
	            //After Country Brand record is created, start a workflow for user to evaluate each record
	            List<Record> records = VaultCollections.newList();
	
	            //Check newly created record is in the initial state (pending_state__c), which has the evaluate_country_brand_useraction__c.
	            recordTriggerContext.getRecordChanges().stream().forEach(rc -> {
	                if (rc.getNew().getValue("state__v", ValueType.STRING).equals("pending_state__c")) {
	                    records.add(rc.getNew());
	                }
	            });
	
	            //Use Job Service to start workflow for each new record by invoking evaluate_country_brand_useraction__c user action.
	            jobParameters.setValue("user_action_name", "evaluate_country_brand_useraction__c");
	            jobParameters.setValue("records", records);
	            jobService.run(jobParameters);
	
	        } else if (recordEvent.toString().equals("AFTER_UPDATE")) {
	
	            //Before Country Brand is updated, update parent Product record to:
	            //    From "Pending" to "In Use" state if any Country Brand record becomes "Active"
	            //    From "In Use" to "Off Market" state if all Country Brand records become "Terminated" or "Not Applicable"
	
	            recordTriggerContext.getRecordChanges().stream().forEach(rc -> {
	
	                String id = rc.getNew().getValue("id", ValueType.STRING);
	                String state = rc.getNew().getValue("state__v", ValueType.STRING);
	                String parentProductId = rc.getNew().getValue("product__c", ValueType.STRING);
	
	                //Use Record Service to read the parent Product record and use the record as input later in the job
	                RecordService recordService = ServiceLocator.locate(RecordService.class);
	                Record parentRecord = recordService.newRecordWithId("vsdk_product__c", parentProductId);
	                List<Record> parentProductRecords = VaultCollections.newList();
	                parentProductRecords.add(parentRecord);
	
	                //Retrieve the state__v field value by reading the parent Product record
	                ReadRecordsResponse readRecordsResponse = recordService.readRecords(parentProductRecords);
	                Map<String, Record> recordMap = readRecordsResponse.getRecords();
	                String parentState = recordMap.get(parentProductId).getValue("state__v", ValueType.STRING);
	
	                //If the vSDK Country Brand record becomes "Active", parent Product record should be "In Use".
	                if (state != null && state.equals("active_state__c")) {
	
	                    if (parentState != null && !parentState.equals("in_use_state__c")) {
	                        //Run job to invoke user action to change parent Product record state
	                        jobParameters.setValue("user_action_name", "change_state_to_in_use_useraction__c");
	                        jobParameters.setValue("records", parentProductRecords);
	                        jobService.run(jobParameters);
	                    }
	
	                } else if (state != null && (state.equals("terminated_state__c") || state.equals("not_applicable_state__c"))) {
	
	                    //Query all Country Brand records for the same parent Product record
	                    QueryService queryService = ServiceLocator.locate(QueryService.class);
	                    String queryCountryBrand = "select id, state__v from vsdk_country_brand__c where vsdk_product__cr.id=" + "\'" + parentProductId + "\'" + "and id !=" + "\'" + id + "\'";
	                    QueryResponse queryResponse = queryService.query(queryCountryBrand);
	                    Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();
	
	                    //Check Country Brand records retrieved are all "Terminated" or "Not Applicable".
	                    boolean allTerminated = true;
	                    while (iterator.hasNext()) {
	                        QueryResult qr = (QueryResult) iterator.next();
	                        String nextChildState = qr.getValue("state__v", ValueType.STRING);
	                        if (nextChildState != null && !nextChildState.equals("terminated_state__c") && !nextChildState.equals("not_applicable_state__c")) {
	                            allTerminated = false;
	                            break;
	                        }
	                    }
	
	                    //If all vSDK Country Brand records for the parent vSDK Product record are terminated, update parent vSDK Product record to "Off Market".
	                    if (allTerminated && (parentState != null && !parentState.equals("pending_state__c") && !parentState.equals("off_market_state__c"))) {
	                        jobParameters.setValue("user_action_name", "change_state_to_off_market_useraction__c");
	                        jobParameters.setValue("records", parentProductRecords);
	                        jobService.run(jobParameters);
	                    }
	
	                }
	            });
	        }
    	}
    }
}