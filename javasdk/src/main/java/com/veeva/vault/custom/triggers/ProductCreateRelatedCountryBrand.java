package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.udc.RequestContextObject;
import com.veeva.vault.custom.udc.UserDefinedUtils;
import com.veeva.vault.custom.udc.RequestContextObject.QueryObject;
import com.veeva.vault.custom.udc.RequestContextObject.ResponseObject;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 *  Automatically create complex join object records in Country Brand object based on the Region on the Product record
 *  
 *  There are two methods demonstrated:
 *  	#1 Region and Country map is built from a query ran in the local context
 *  		- Query vsdk_country__c for countries that contain the region configured on the vsdk_product__c record
 *  		- Create a hashmap that maps the region (key) to a list of vsdk_country__c query results (value).
 *  		- Iterate over the hashmap to create vsdk_country_brand__c records for each regions list of vsdk_country__c records.
 *  
 *  	#2 Region and Country map are previously created in with a Request Context.
 *  		- Pull in the requestContextObject object with a preconstructed "countriesInRegionMap" hashmap.
 *  		- Iterate over "countriesInRegionMap" to create vsdk_country_brand__c records for each regions list of vsdk_country__c records.
 *  
 *  The second method allows the requestContextObject to be stored and retrieved across multiple triggers that are running in the same context.
 *  This is helpful when you have data that is relevant across multiple triggers - such as a hashmap of queried data.
 *  
 */

@RecordTriggerInfo(object = "vsdk_product__c", name= "vsdk_product_create_related_country_brand__c", events = RecordEvent.AFTER_INSERT)
public class ProductCreateRelatedCountryBrand implements RecordTrigger  {

    public void execute(RecordTriggerContext recordTriggerContext) {
    	
        RecordService recordService = ServiceLocator.locate(RecordService.class);
        List<Record> recordList;
    	
    	//If testing the REQUEST_CONTEXT methods, the queried information will be grabbed from the RequestContext. 
    	//Otherwise, run the query and build the HashMap directly in the trigger.
    	if (UserDefinedUtils.example_type.equals(UserDefinedUtils.REQUEST_CONTEXT)){
    		recordList = executeRequestContext(recordTriggerContext);
    	}
    	else{
    		recordList = executeLocalContext(recordTriggerContext);
    	}

        // Save the new vSDK Country Brand records in bulk. Rollback the entire transaction when encountering errors.
    	if (recordList.size() > 0) {
	        recordService.batchSaveRecords(recordList)
                .onErrors(batchOperationErrors -> {
                	UserDefinedUtils.throwBatchErrors(recordList, batchOperationErrors);
                })
                .execute();
    	}
    }
    
    // Query for vSDK Country records in the local trigger and build a Hashmap of Regions -> Country queries. Once built, creates vSDK Country Brand records.
    private List<Record> executeLocalContext(RecordTriggerContext recordTriggerContext){
    	
        RecordService recordService = ServiceLocator.locate(RecordService.class);
        List<Record> recordList = VaultCollections.newList();
        
    	 // Retrieve Regions from all Product records
        Set<String> regions = VaultCollections.newSet();
        recordTriggerContext.getRecordChanges().stream().forEach(recordChange -> {
            String regionId = recordChange.getNew().getValue("region__c", ValueType.STRING);
            regions.add("'" + regionId + "'");
        });
        String regionsToQuery = String.join (",",regions);

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

        // Go through each Product record, look up countries for the region assigned to the Product,
        // and create new Country Brand records for each country.
        for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {

            String regionId = inputRecord.getNew().getValue("region__c", ValueType.STRING);
            String internalName = inputRecord.getNew().getValue("internal_name__c", ValueType.STRING);
            String productId = inputRecord.getNew().getValue("id", ValueType.STRING);

            Iterator<QueryResult> countries = countriesInRegionMap.get(regionId).iterator();

            while (countries.hasNext()){
                QueryResult country =countries.next();
                Record r = recordService.newRecord("vsdk_country_brand__c");
                r.setValue("name__v", internalName + " (" + country.getValue("name__v", ValueType.STRING) + ")");
                r.setValue("country__c", country.getValue("id", ValueType.STRING));
                r.setValue("product__c", productId);
                
                //*****Comment out below setValue to demonstrate the Test_UDC.throwBatchErrors User Defined Class method.
                r.setValue("brand_name__c", "Test Brand Name"); 
                
                recordList.add(r);
            }
        }
		return recordList;
    }
    
    // Retrieve the Regions -> Country queries Hashmap from the Request Context and then build the vSDK Country Brand records.
    private List<Record> executeRequestContext(RecordTriggerContext recordTriggerContext){
    	
    	
        RecordService recordService = ServiceLocator.locate(RecordService.class);
        List<Record> recordList = VaultCollections.newList();
        
        // Retrieve the preconstructed RequestContextObject object that contains context-wide settings.
        RequestContextObject requestContextObject = RequestContext.get().getValue("requestContextObject", RequestContextObject.class);
        
    	// Retrieve the Region -> Country Query HashMap from the RequestContextObject.
        Map<String, QueryObject> countriesInRegionMap = requestContextObject.getMap();
        
        // Go through each Product record, look up countries for the region assigned to the Product,
        // and create new Country Brand records for each country.
        for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {

            String regionId = inputRecord.getNew().getValue("region__c", ValueType.STRING);
            String internalName = inputRecord.getNew().getValue("internal_name__c", ValueType.STRING);
            String productId = inputRecord.getNew().getValue("id", ValueType.STRING);

            Iterator<ResponseObject> countries = countriesInRegionMap.get(regionId).getItems().iterator();

            while (countries.hasNext()){
                ResponseObject country = countries.next();
                Record r = recordService.newRecord("vsdk_country_brand__c");
                r.setValue("name__v", internalName + " (" + country.getField("name__v") + ")");
                r.setValue("country__c", country.getField("id"));
                r.setValue("product__c", productId);
                
              //*****Comment out below setValue to demonstrate the Test_UDC.throwBatchErrors User Defined Class method.
                r.setValue("brand_name__c", "Test Brand Name"); 
                
                recordList.add(r);
            }
        }
		return recordList;
    }
}