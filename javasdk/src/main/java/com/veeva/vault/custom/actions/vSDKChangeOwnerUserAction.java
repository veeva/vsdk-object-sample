package com.veeva.vault.custom.actions;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.action.RecordAction;
import com.veeva.vault.sdk.api.action.RecordActionContext;
import com.veeva.vault.sdk.api.action.RecordActionInfo;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.group.Group;
import com.veeva.vault.sdk.api.group.GroupService;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryService;
import com.veeva.vault.sdk.api.role.GetRecordRolesResponse;
import com.veeva.vault.sdk.api.role.RecordRole;
import com.veeva.vault.sdk.api.role.RecordRoleService;
import com.veeva.vault.sdk.api.role.RecordRoleUpdate;


import java.util.List;
import java.util.Map;
import java.util.Set;

/**
*
* This User Action assigns the current Owner to the "Owner__c" field of a vSDK Product record to the "Owner" role on each related vSDK Country Brand record. 
*
*  Create RecordRoleService object to set the "Owner" role for the current record: 
*  
*  			#1 Set the "Owner__c" field of the vSDK Product to the current user and save the record. 
*           #2 Create a map of vSDK Product ID to the new owner in the case that their are multiple records.
*           #3 On successful insert, query for all the related vSDK Country Brand records that need to be updated and pass the them to changeOwnerRole().
*   		#4 Retrieve the RecordRoles for all vSDK Country Brand records with GetRecordRolesResponse
*   		#5 Iterate over the GetRecordRolesResponse and retrieve the "owner__v" RecordRole for each record
*   		#6 Check if the new owner is in the "owner__v" role with RecordRoleService.getUserInRecordRoles
*   		#7 Check if the Vault Owner group is in the "owner__v" role with RecordRoleService.getGroupInRecordRoles
*   		#8 If the user is not in the role or in the Vault Owner group, add the current user to the "owner__v" role for each vSDK Country Brand record.
*
*/

@RecordActionInfo(label="Run vSDK Change Owner User Action")
public class vSDKChangeOwnerUserAction implements RecordAction {
	
    public void execute(RecordActionContext recordActionContext) {

    	RequestContext requestContext = RequestContext.get();
    	RecordService recordService = ServiceLocator.locate(RecordService.class);
    	QueryService queryService = ServiceLocator.locate(QueryService.class);
    	
    	List<Record> productRecordList = recordActionContext.getRecords();
    	List<Record> countryBrandRecordList = VaultCollections.newList();
    	
        Set<String> productIds = VaultCollections.newSet();
        Map<String, String> newOwnerMap = VaultCollections.newMap();
    	
        //Create a map of product Id (key) to new owner Id (value). 
    	//This allows the trigger to match the queried vSDK Country Brand to the proper owners.
        String newOwner = requestContext.getCurrentUserId();
        
        for (Record inputRecord : productRecordList) {
        	String productId = inputRecord.getValue("id", ValueType.STRING);
        	inputRecord.setValue("owner__c", newOwner);
        	newOwnerMap.put(productId, newOwner);
        	productIds.add("'" + productId + "'");
        }
        
    	if (productRecordList.size() > 0) {
	        recordService.batchSaveRecords(productRecordList)
                .rollbackOnErrors().execute();
	        
            //Queries for related vSDK Country Brand records that need to be updated to a new owner based on the vSDK Product Id.
            //A list of Records is then built and sent to the "changeOwnerRole()" function.
            String query = "select id from vsdk_country_brand__c where product__c contains (" + String.join (",",productIds) + ")";
            QueryResponse queryResponse = queryService.query(query);
            
            queryResponse.streamResults().forEach(queryResult -> {
         	   String id = queryResult.getValue("id",ValueType.STRING);
         	   Record country_brand = recordService.newRecordWithId("vsdk_country_brand__c", id);
         	   country_brand = recordService.readRecords(VaultCollections.asList(country_brand)).getRecords().get(id);
         	   countryBrandRecordList.add(country_brand); 
            });
            
            changeOwnerRole(countryBrandRecordList, newOwnerMap);
    	}
    }
    
    //The "changeOwnerRole() function manages the RecordRoleService and modifies the owner roles for each of the vSDK Country Brand records
    //found in the query.
    private void changeOwnerRole(List<Record> recordList, Map<String,String> userIdMap) {
    	
    	RecordRoleService recordRoleService = ServiceLocator.locate(RecordRoleService.class);
    	GroupService groupService = ServiceLocator.locate(GroupService.class);
    	List<RecordRoleUpdate> recordRoleUpdateList = VaultCollections.newList();
    	
    	//Add a check to verify is the specified Reviewer is in the Vault Owner group. This group is defaulted on new records.
        Group vaultOwnerGroup = groupService
        	       .getGroupsByNames(VaultCollections.asList("vault_owners__v"))
        	       .getGroupByName("vault_owners__v");
        
        //Get the "GetRecordRolesResponse" for the records being updated.
        //Then iterate over the list of records to get the RecordRole for the "owner__v" role and see if the Reviewer is in that role.
        GetRecordRolesResponse recordRoles = recordRoleService.getRecordRoles(recordList, "owner__v");
        
    	for (Record inputRecord : recordList) {
    		
    		String productId = inputRecord.getValue("product__c",ValueType.STRING);
    	
	        RecordRole ownerRole = recordRoles.getRecordRole(inputRecord);
	
	        boolean isCurrentUserInGroup = groupService.isUserInGroup(userIdMap.get(productId), vaultOwnerGroup);
	        
	        // Check if Reviewer user is in Owner role directly or due to a group
	        boolean isUserInOwnerRole = recordRoleService
	           .getUserInRecordRoles(userIdMap.get(productId), VaultCollections.asList(ownerRole))
	           .isUserInRecordRole(ownerRole);
	        
	        // Check if Vault Owner Group assigned to the record role
	        boolean isGroupInOwnerRole = recordRoleService
	           .getGroupInRecordRoles(vaultOwnerGroup, VaultCollections.asList(ownerRole))
	           .isGroupInRecordRole(ownerRole);
	        
	        // Create an RecordRoleUpdate to add the Reviewer user to Owner role if they aren't already in the role and delete the existing owner.
	        if (!isUserInOwnerRole || (isCurrentUserInGroup && isGroupInOwnerRole)) {
	        	RecordRoleUpdate recordRoleUpdate = recordRoleService.newRecordRoleUpdate("owner__v", inputRecord);
		        recordRoleUpdate.removeUsers(ownerRole.getUsers());
		        recordRoleUpdate.removeGroups(ownerRole.getGroups());
		        recordRoleUpdate.addUsers(VaultCollections.asList(userIdMap.get(productId)));
		        recordRoleUpdateList.add(recordRoleUpdate);
	        }
    	}
    	
    	// Run the batch to submit the RecordRoleUpdate changes
        if (recordRoleUpdateList.size() > 0) {
	        recordRoleService.batchUpdateRecordRoles(recordRoleUpdateList)
	           .rollbackOnErrors()
	           .execute();
        }
    }
    
	public boolean isExecutable(RecordActionContext recordActionContext) {
		return true;
	}
}