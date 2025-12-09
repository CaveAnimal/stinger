# Development Goals: Analysis Results Reformatting & Real-time Progress

**Attention Developer:**
You are tasked with updating the Analysis Results display. These requirements are strict. **Follow these specifications EXACTLY.** Do not deviate. Do not add "cool features" or "improvements" unless explicitly requested. Stick to the requirements below.

## Objective
Reformat the Analysis Results screen to provide real-time feedback during the analysis process.

## Detailed Requirements

### General Behavior
*   The display **must** update in real-time as the analysis proceeds.
*   **Every** section listed below is mandatory.
*   **Every** progress bar listed below is mandatory.
*   **Every** percentage display listed below is mandatory.

### UI Layout & Structure
Implement the following sections in the **exact order** listed:

#### 1. Folders Section
*   **Display Text:** Show the name of the **Current Folder** being processed.
*   **Visual:** Render a Progress Bar for the count of Folders processed vs. total.
*   **Metric:** Display the numeric **Percent Complete** for Folders.

#### 2. Code Files Section
*   **Display Text:** Show the name of the **Current Code File** being processed.
*   **Visual:** Render a Progress Bar for the count of Code Files processed.
*   **Metric:** Display the numeric **Percent Complete** for Code Files.
*   **Visual:** Render a secondary Progress Bar for **Lines of Code** processed.
*   **Metric:** Display the numeric **Percent Complete** for Lines of Code.

#### 3. Methods Section
*   **Display Text:** Show the name of the **Current Method** being analyzed.
*   **Visual:** Render a Progress Bar for the count of Methods processed.
*   **Metric:** Display the numeric **Percent Complete** for Methods.

#### 4. Document Files Section
*   **Display Text:** Show the name of the **Current Document File** being processed.
*   **Visual:** Render a Progress Bar for the count of Doc Files processed.
*   **Metric:** Display the numeric **Percent Complete** for Doc Files.
*   **Visual:** Render a secondary Progress Bar for **Lines of Documents** processed.
*   **Metric:** Display the numeric **Percent Complete** for Lines of Documents.

#### 5. Totals Section
*   **Visual:** Render a Progress Bar for **Total Files** (Code + Doc).
*   **Metric:** Display the numeric **Percent Complete** for Total Files.
*   **Visual:** Render a Progress Bar for **Total Lines** (Code + Doc).
*   **Metric:** Display the numeric **Percent Complete** for Total Lines.

## Definition of Done
*   The UI matches the structure above exactly.
*   All progress bars animate smoothly or update frequently enough to show activity.
*   No "Current Item" text is left blank during processing.
