package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.BatchOperationError;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

import java.util.List;

/**
*
* This is an example User Defined Class with re-usable generic methods. It also configures a "example_type" variable to define specific trigger functionality to run.
*
* To run a particular set of triggers, set the "example_type" variable to one of the following:
* 
* 			ALL              - this will run through all the triggers and initiate workflows and user actions in CountryBrandLifeCycleUserActions.java
* 			NO_WORKFLOW      - this will run all triggers but not run workflows or user actions.
* 			REQUEST_CONTEXT  - this will run through triggers demonstrating RequestContext functionality in ProductInitializeRequestContextValue.java and ProductCreateRelatedCountryBrand.java
* 
*/

@UserDefinedClassInfo(name="vsdk_userdefinedutils__c")
public class UserDefinedUtils {
	
  public static final String REQUEST_CONTEXT = "request_context";
  public static final String NO_WORKFLOW = "no_workflow";
  public static final String ALL = "all";
  
  
  
  //SET THIS VARIABLE TO ALL, NO_WORKFLOW, or REQUEST_CONTEXT TO CHANGE BEHAVIOR
  public static final String example_type = ALL;
  
  

 // Generic method to catch errors and display them for use with batch operations.  
  public static void throwBatchErrors(List<Record> recordList, List<BatchOperationError> batchOperationErrors){
	  
	      batchOperationErrors.stream().findFirst().ifPresent(error -> {
	          String errMsg = error.getError().getMessage();
	          int errPosition = error.getInputPosition();
	          String name = recordList.get(errPosition).getValue("name__v", ValueType.STRING);
	          throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to create '" + recordList.get(errPosition).getObjectName() + "' record: '" +
	                  name + "' because of '" + errMsg + "'.");
	      });
  }
  
  //Generic method for requiring a "dependent" field - ie, require a second "dependent" field when another "controlling" field is set.  
  public static <T, S> void requiredDependentField(RecordChange inputRecord, String controllingField, ValueType<S> controllingType, String dependentField, ValueType<T> dependentType){
	  
      Object newControllingFieldValue = inputRecord.getNew().getValue(controllingField, controllingType);

      if (newControllingFieldValue != null){
    	  
    	  if (controllingType == ValueType.BOOLEAN && newControllingFieldValue.toString().equals("true")) {
	          Object newDependentFieldValue = inputRecord.getNew().getValue(dependentField, dependentType);
	          if (newDependentFieldValue == null) {
	              inputRecord.setError("OPERATION_NOT_ALLOWED", dependentField + " is required.");
	          }
    	  }
    	  else if (controllingType != ValueType.BOOLEAN && !newControllingFieldValue.toString().equals("")){
	          Object newDependentFieldValue = inputRecord.getNew().getValue(dependentField, dependentType);
	          if (newDependentFieldValue == null) {
	              inputRecord.setError("OPERATION_NOT_ALLOWED", dependentField + " is required.");
	          }
    	  }
      }   
  } 
}
