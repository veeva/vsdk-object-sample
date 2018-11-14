package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.udc.RequestContextObject;
import com.veeva.vault.custom.udc.UserDefinedUtils;
import com.veeva.vault.custom.udc.RequestContextObject.QueryObject;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * This trigger validates field values. This trigger runs after the ProductFieldDefaults trigger in order
 * to verify that the Expected Date value is defaulted by that trigger.
 *
 */

@RecordTriggerInfo(object = "vsdk_product__c", name="vsdk_product_field_value_validations__c", events = {RecordEvent.BEFORE_INSERT, RecordEvent.BEFORE_UPDATE}, order = TriggerOrder.NUMBER_3)
public class ProductFieldValueValidations implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {
    	
    	//If testing the REQUEST_CONTEXT methods, the queried information will be grabbed from the RequestContext. 
    	//Otherwise, run the query and build the HashMap directly in the trigger.
    	if (UserDefinedUtils.example_type.equals(UserDefinedUtils.REQUEST_CONTEXT)){
    		executeRequestContext(recordTriggerContext);
    	}
    	else{
    		executeLocalContext(recordTriggerContext);
    	}
    }
    
    // Retrieve the Regions -> Country queries Hashmap from the Request Context and then validates the Region and Expected Date fields.
    private void executeRequestContext(RecordTriggerContext recordTriggerContext) {
		
		//Retrieve the preconstructed requestContextObject from the RequestContext
		RequestContextObject requestContextObject = RequestContext.get().getValue("requestContextObject", RequestContextObject.class);
		
		for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
        	
            LocalDate expectedDate = inputRecord.getNew().getValue("expected_date__c",ValueType.DATE);
            String state  = inputRecord.getNew().getValue("state__v", ValueType.STRING);
            String regionId = inputRecord.getNew().getValue("region__c", ValueType.STRING);
        	
        	if (UserDefinedUtils.example_type.equals(UserDefinedUtils.REQUEST_CONTEXT)){
        		
            	// Retrieve the Region -> Country Query HashMap from the RequestContextObject.
                Map<String, QueryObject> countriesInRegionMap = requestContextObject.getMap();
                
                //Verifies that the vSDK Product's region has associated vSDK Country records using a RequestContext object. If not, the triggers will error out.
                if (countriesInRegionMap.get(regionId) == null) {
                	inputRecord.setError("OPERATION_NOT_ALLOWED", "The vSDK Region does not have any associated vSDK Countries.");
                }
                // Expected date must be today or in the future when the vSDK Product is in a pending or null state.
                else {
                    if (expectedDate != null && expectedDate.isBefore(LocalDate.now()) && (state.equals("pending_state__c") || state == null))
                        inputRecord.setError("OPERATION_NOT_ALLOWED", "Expected Date cannot be in the past.");  
                }
        	}
		}
	}

    // Locally build the Regions -> Country queries Hashmap from the Request Context and then validates the Region and Expected Date fields.
	private void executeLocalContext(RecordTriggerContext recordTriggerContext) {
		
    	// Retrieve Regions from all Product records
        Set<String> regions = VaultCollections.newSet();
        recordTriggerContext.getRecordChanges().stream().forEach(recordChange -> {
            String regionId = recordChange.getNew().getValue("region__c", ValueType.STRING);
            regions.add("'" + regionId + "'");
        });
        String regionsToQuery = String.join (",",regions);
        
		for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
        	
            LocalDate expectedDate = inputRecord.getNew().getValue("expected_date__c",ValueType.DATE);
            String state  = inputRecord.getNew().getValue("state__v", ValueType.STRING);
            String regionId = inputRecord.getNew().getValue("region__c", ValueType.STRING);

    		// Query Country object to select countries for regions referenced by all Product input records
            QueryService queryService = ServiceLocator.locate(QueryService.class);
            String queryCountry = "select id, name__v, region__c " +
                    "from vsdk_country__c where region__c contains (" + regionsToQuery + ")";
            QueryResponse queryResponse = queryService.query(queryCountry);

            // Create a Map of Regions (key) and Countries (value) from the query result
            Map<String, List<QueryResult>> countriesInRegionMap = VaultCollections.newMap();

            queryResponse.streamResults().forEach(queryResult -> {
                String region = queryResult.getValue("region__c",ValueType.STRING);
                if (countriesInRegionMap.containsKey(region)) {
                    List<QueryResult> countries = countriesInRegionMap.get(region);
                    countries.add(queryResult);
                    countriesInRegionMap.put(region,countries);
                } else
                    countriesInRegionMap.putIfAbsent(region,VaultCollections.asList(queryResult));
            });
            
            //Verifies that the vSDK Product's region has associated vSDK Country records. If not, the triggers will error out.
            if (countriesInRegionMap.get(regionId) == null) {
            	inputRecord.setError("OPERATION_NOT_ALLOWED", "The vSDK Region does not have any associated vSDK Countries.");
            }
            // Expected date must be today or in the future when the vSDK Product is in a pending or null state.
            else {
                if (expectedDate != null && expectedDate.isBefore(LocalDate.now()) && (state.equals("pending_state__c") || state == null))
                    inputRecord.setError("OPERATION_NOT_ALLOWED", "Expected Date cannot be in the past.");  
            }
        }
	}
}

