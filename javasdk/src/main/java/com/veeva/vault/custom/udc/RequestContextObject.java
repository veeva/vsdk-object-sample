package com.veeva.vault.custom.udc;

import java.util.List;
import java.util.Map;
import com.veeva.vault.sdk.api.core.RequestContextValue;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

/**
*
* This is an example Request Context class (extends RequestContextValue) that enables the storage of a map of 
* vSDK Region (Key) to list of associated vSDK Countries. The RequestContext functionality allows data to be passed 
* from the initial firing to subsequent triggers within the same transaction. 
* 
* RequestContextValue objects only allow the following retrievable fields:
* 	- Static immutable fields (for example, constants)
* 	- Non-static fields of type:
* 		- RequestContextValueType
* 		- RequestContextValue
* 		- Collection of class type RequestContextValueType or RequestContextValue
* 		- Map whose key and value must be of class type RequestContextValueType or RequestContextValue
*
* This means that a Map<String, List<QueryResult>> CANNOT used. Instead you have to build a custom object (QueryObject) 
* with a list of the queried vSDK Country results (ResponseObjects).
* 
* Building the Map<String, QueryObject> regionCountryMap:
*  		- Query vsdk_country__c for countries that contain the vSDK Region configured on the vsdk_product__c record
*  		- Create a hashmap of the region (key) to a custom QueryObject (value) that contains a list of ResponseObjects. 
*  			- The ResponseObjects contains the ID, name__v, and region__c of the vsdk_country__c query results (value).
*  		- The RequestContextObject can be initialized with "new RequestContextObject(regionsToQuery)";
*/

@UserDefinedClassInfo(name = "vsdk_requestcontextobject__c")
public class RequestContextObject implements RequestContextValue {

   private Map<String, QueryObject> regionCountryMap = VaultCollections.newMap();
   public String startTimestamp = null;
   public String example_type = UserDefinedUtils.ALL;
   
   public RequestContextObject(){}

   public RequestContextObject (String regionsToQuery){

   // Constructor to create a regionCountryMap <region, countries> by querying the Region object 
   // to retrieve countries in the provided regions.	   
       QueryService queryService = ServiceLocator.locate(QueryService.class);
       String queryCountry = "select id, name__v, region__c " +
               "from vsdk_country__c where region__c contains (" + regionsToQuery + ")";
       QueryResponse queryResponse = queryService.query(queryCountry);
       setMap(this.createQueryMap(queryResponse, "region__c", ValueType.STRING));
   }  
   
// Generic method to create a Map where the defined keyField (key) maps to records (value) from the query result.	
   public <T> Map<String, QueryObject> createQueryMap (QueryResponse queryResponse, String keyField, ValueType<T> fieldType){

      Map<String, QueryObject> queryMap = VaultCollections.newMap();
      queryResponse.streamResults().forEach(queryResult -> {
          Object keyString = queryResult.getValue(keyField,fieldType);
          if (keyString != null){
              if (queryMap.containsKey(keyString.toString())) {
            	  QueryObject queryObject = queryMap.get(keyString.toString());
                  queryObject.addItem(queryResult);
                  queryMap.put(keyString.toString(),queryObject);
              } else{
            	  QueryObject queryObject = new QueryObject(); 
            	  queryObject.addItem(queryResult);
            	  queryMap.putIfAbsent(keyString.toString(),queryObject); 
              }
          }
      });
      
      return queryMap;
   }
   
   public void setMap (Map<String, QueryObject> updatedMap) {
       regionCountryMap = updatedMap;
   }

   public Map<String, QueryObject> getMap () {
       return regionCountryMap;
   }
   
   public static class QueryObject implements RequestContextValue {
	   private List<ResponseObject> results = VaultCollections.newList();
	   
	   public QueryObject(){}
	   
	   public void addItem(QueryResult response){
		   results.add(new ResponseObject(response));
	   }
	   
	   public List<ResponseObject> getItems(){
		   return results;
	   }
   }
   
   public static class ResponseObject implements RequestContextValue {
	   private String id, name__v, region__c;
	   
	   public ResponseObject(){}
	   
	   public ResponseObject(QueryResult response) {
		   id = response.getValue("id", ValueType.STRING);
		   name__v = response.getValue("name__v", ValueType.STRING);
		   region__c = response.getValue("region__c", ValueType.STRING);    
	   }
       public String getField(String field) {
    	   
    	   if (field == "name__v") {
               return name__v;
    	   }
    	   else if (field == "id") {
               return id;
    	   }
    	   else if (field == "region__c") {
               return region__c;
    	   }
    	   else {
    		   return null;
    	   }
       }
       
       public void setField(String field, String value) {
    	   
    	   if (field == "name__v") {
               name__v = value;
    	   }
    	   else if (field == "id") {
               id = value;
    	   }
    	   else if (field == "region__c") {
               region__c = value;
    	   }
       }
   }
}