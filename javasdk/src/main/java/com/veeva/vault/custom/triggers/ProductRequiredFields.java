package com.veeva.vault.custom.triggers;

import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;


/**
 *
 * This trigger makes certain fields required based on values in other fields
 *
 */

@RecordTriggerInfo(object = "vsdk_product__c", name="vsdk_product_required_fields__c", events = {RecordEvent.BEFORE_INSERT, RecordEvent.BEFORE_UPDATE}, order = TriggerOrder.NUMBER_4)
public class ProductRequiredFields implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {
    	
    	for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
    		Boolean compound = inputRecord.getNew().getValue("compound__c", ValueType.BOOLEAN);
        
	    	//Verifies if the compound__c field is marked as true. If it is, require the compound_id__c field.
	        if (compound != null && compound) {
	            String compoundId = inputRecord.getNew().getValue("compound_id__c", ValueType.STRING);
	            if (compoundId == null) {
	                inputRecord.setError("OPERATION_NOT_ALLOWED", "Compound ID is required.");
	            }
	        } 
	        
	        //To check for required fields via a user-defined class instead:
	        //  - Comment out the above IF statement
	        //  - Uncomment the below UserDefinedUtils.requiredDependentField line
	        
	        //UserDefinedUtils.requiredDependentField(inputRecord, "compound__c", ValueType.BOOLEAN, "compound_id__c", ValueType.STRING); 
    	}
    }
}
