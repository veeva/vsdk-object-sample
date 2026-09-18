package com.veeva.vault.custom.triggers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.veeva.vault.custom.udc.RequestContextObject;
import com.veeva.vault.custom.udc.UserDefinedUtils;
import com.veeva.vault.custom.udc.RequestContextObject.QueryObject;
import com.veeva.vault.custom.udc.RequestContextObject.ResponseObject;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.*;

/**
 *
 * This trigger initializes a RequestContextObject object and its fields for use with the RequestContext functionality.
 * The RequestContext functionality allows data to be passed from the initial firing to subsequent triggers within the same transaction. 
 *
 */

@RecordTriggerInfo(object = "vsdk_product__c", name="vsdk_initialize_request_context__c", events = {RecordEvent.BEFORE_INSERT, RecordEvent.BEFORE_UPDATE}, order = TriggerOrder.NUMBER_1)
public class ProductInitializeRequestContextValue implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {
    	String example_type = UserDefinedUtils.example_type;
    	
    	if (UserDefinedUtils.example_type.equals(UserDefinedUtils.REQUEST_CONTEXT)){
    		
            // Retrieve Regions from all Product records and pass the region IDs to the RequestContextObject to run the QueryService and create a hashmap.
            // The IDs are passed as a List and resolved through a TokenRequest inside RequestContextObject rather than concatenated into a query String.
            List<String> regionIds = VaultCollections.newList();

            recordTriggerContext.getRecordChanges().stream().forEach(recordChange -> {
                String regionId = recordChange.getNew().getValue("region__c", ValueType.STRING);
                regionIds.add(regionId);
            });


            //Initialize the requestContextObject object and set additional fields on the object. Once ready, save the object to the RequestContext for re-use with
            //RequestContext.get().setValue()
            RequestContextObject requestContextObject = new RequestContextObject(regionIds);
            
            requestContextObject.startTimestamp = String.valueOf(Instant.now().toEpochMilli());
            requestContextObject.example_type = example_type;
            

            RequestContext.get().setValue("requestContextObject", requestContextObject);
       
    	}
    }
}