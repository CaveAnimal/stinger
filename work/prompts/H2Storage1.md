Use the files Bottom-UpCodebaseAnalysis2.md and SummaryDefinition2.md to build a persistant H2 storage structure for each application that will be analized.
Later, this H2 database will be stored in a vecor database to enable fast retrieval of information about each application based on the users question.
All information feom each application must be kept separate from all other applications to avoid any cross contamination of data.
The H2 database must be able to store information about the following items for each application:
- Application Name
- Application Version
- The application-relative or project-relative file path for each node (method/paragraph, file, folder) analyzed
- The type of each node (method/paragraph, file, folder)
- The json object summaries of each node (method/paragraph code, file content, folder content summary) tha was returned from the llama-service call.

Use the latest results for each applicaiton from /code_counter_results for an application identified by the root folder name.
Add a folder under /code_summary_results similar to the /code_counter_results structure to store the H2 database files for each application.
Keep a copy of the code files from code_counter_results and prepent each file line with a symbol [x] when the file has been processed so that this application can be restarted if interupted for any reason, we can continue where we left off.
Design the H2 database schema to efficiently store and retrieve the required information for each application.
Here is a suggested H2 database schema to store the required information for each application:
