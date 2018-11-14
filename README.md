# Vault Java SDK Sample - vsdk-object-sample

**Please see the [project wiki](https://github.com/veeva/vsdk-object-sample/wiki) for a detailed walkthrough.**

The vsdk-object-sample project covers the creation of a vSDK Product record and related vSDK Country Brand records. The project will step through:

* Field defaulting, field validation, and conditionally required fields on the vSDK Product record.
* Creating related vSDK Country Brand records based on the vSDK Region associated with the vSDK Product record.
* The creation of the vSDK Country Brand records will kick off a workflow on each country brand record.
* The completion of the workflow will trigger a state change (update) to the vSDK Country Brand record and it's associated vSDK Product record.
* A custom User Action to change the owner role of a vSDK Product's related vSDK Country Brand records.

## How to import

Import the project as a Maven project. This will automatically pull in the required Vault Java SDK dependencies. 

For Intellij this is done by:
-	File -> Open -> Navigate to project folder -> Select the 'pom.xml' file -> Open as Project

For Eclipse this is done by:
-	File -> Import -> Maven -> Existing Maven Projects -> Navigate to project folder -> Select the 'pom.xml' file


## Setup

The project contains two separate vault packages (VPK) in the "deploy-vpk" directory with the necessary Objects, Actions, Lifecycles, Workflows, and custom object actions for the following examples.

The  **VPKs need to be deployed to your vault**  prior to debugging these use cases:

1.  Clone or download the sample Maven project [vSDK Object Sample project](https://github.com/veeva/vsdk-object-sample) from GitHub.
2.  Run through the [Getting Started](https://developer.veevavault.com/sdk/#Getting_Started) guide to setup your deployment environment.
3.  Log in to your vault and navigate to **Admin > Deployment > Inbound Packages** and click **Import**: 

    > Deploy Object Action code:  Select the **\deploy-vpk\code\vsdk-object-sample-action-code.vpk** file.

4.  From the **Actions** menu, select **Review & Deploy**. Vault displays a list of all components in the package.  
5.  Review the prompts to deploy the package. You will receive an email when the deployment is complete.
6.  Repeat steps 3-5 for the vault components:

    > Deploy vault components:  Select the **\deploy-vpk\vsdk-object-sample-components\vsdk-object-sample-components.vpk** file.


## How to run

To see the project in action, configure the  [debugger](https://developer.veevavault.com/sdk/#Debug_Setup) and set breakpoints or [deploy](https://developer.veevavault.com/sdk/#Deploy) the code to your Vault.

Once the project is in debug or deployed to vault:

1.  Navigate to your  **Admin -> Business Admin -> vSDK Product** tab in your vault and  **Create** a new record.
2.  Enter any value in the  **Name** field.
3.  Click  **Save**.
4.  While vault attempts to save the record, each of the trigger examples discussed below will run.
	
	    
## License

This code serves as an example and is not meant for production use.

Copyright 2018 Veeva Systems Inc.
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
  