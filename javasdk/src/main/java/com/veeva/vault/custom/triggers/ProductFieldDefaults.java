package com.veeva.vault.custom.triggers;

import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

import java.time.LocalDate;

/**
 *
 * This trigger sets default values on fields.
 * 
 * The inputRecord.getNew() returns a Record object with values from the new insert. The trigger then uses Record.getValue() and Record.setValue() 
 * to retrieve or set data on the new record.
 *
 */

@RecordTriggerInfo(object = "vsdk_product__c",name="vsdk_product_field_defaults__c", events = RecordEvent.BEFORE_INSERT, order = TriggerOrder.NUMBER_2)
public class ProductFieldDefaults implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

        for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {

            // Default Internal Name in a specific format
            String internalName = "BIORAD:" + inputRecord.getNew().getValue("name__v",ValueType.STRING).toLowerCase();
            inputRecord.getNew().setValue("internal_name__c", internalName);

            // Default Expected Date a week ahead if the field is null
            if (inputRecord.getNew().getValue("expected_date__c", ValueType.DATE) == null){
            	inputRecord.getNew().setValue("expected_date__c", LocalDate.now().plusWeeks(1));
            }

            // Default Reviewer to the user creating the record
            String currentUserId = RequestContext.get().getCurrentUserId();
            inputRecord.getNew().setValue("reviewer__c", currentUserId);

        }
    }
}


