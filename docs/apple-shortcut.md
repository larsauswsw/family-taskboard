# Apple Shortcut for Quick Task Entry

This guide shows how to set up Apple Shortcuts to quickly add tasks to Taskboard using the REST API endpoint.

## Overview

The Apple Shortcut automates the following workflow:
1. **Dictate Text**: Capture a voice input or text
2. **POST to /api/tasks/quick**: Send the text to Taskboard with Bearer token authentication
3. **Show Result**: Display the created task confirmation

## Setup Steps

### 1. Get Your API Token

1. Log in to Taskboard
2. Navigate to your user settings
3. Copy your **API Token** (a unique key for authenticating API requests)

### 2. Create the Shortcut

1. Open the **Shortcuts** app on your Mac or iOS device
2. Create a New Shortcut
3. Add these actions in order:

#### Action 1: Dictate Text
- **Type**: "Dictate Text"
- **Settings**: Leave defaults (captures voice or allows text entry)

#### Action 2: Get Contents of URL
- **Type**: "Get Contents of URL"
- **URL**: `https://your-domain.com/api/tasks/quick` (replace `your-domain.com` with your Taskboard server)
- **Method**: `POST`
- **Headers**:
  - Key: `Authorization`
  - Value: `Bearer YOUR_API_TOKEN` (paste your API token from step 1)
- **Request Body**:
  - Type: `Form Data` or `JSON`
  - JSON Body:
    ```json
    {
      "text": "[Dictated Text output]"
    }
    ```

#### Action 3: Show Result
- **Type**: "Show Result"
- **Input**: [Response from previous step]
- Displays the created task details (ID, title, due date)

## Assign to Siri, Back Tap, or Action Button

### Via Siri Phrase
1. In Shortcuts, find your shortcut
2. Tap the `•••` menu
3. **Add to Siri** → Set a voice phrase like "Add task to Taskboard"
4. Confirm and tap **Done**
5. Now say "Hey Siri, Add task to Taskboard" to use it

### Via Back Tap (iPhone/iPad)
1. Go to **Settings** → **Accessibility** → **Touch**
2. Select **Back Tap**
3. Choose a tap pattern (double or triple tap)
4. Assign your Taskboard shortcut
5. Double or triple tap the back of your device to trigger the shortcut

### Via Action Button (iPhone 15 Pro)
1. Go to **Settings** → **Action Button**
2. Select your shortcut
3. Press and hold the Action Button to activate

### Via Lock Screen Widget
1. Long-press your Lock Screen
2. Tap **Customize**
3. Add a **Shortcuts** widget
4. Configure it to show your Taskboard shortcut
5. Tap the widget on Lock Screen to run it

## Sync via iCloud

All shortcuts sync automatically to your Apple Watch via iCloud if enabled:
1. Ensure **iCloud Drive** is enabled in **Settings** → **[Your Name]** → **iCloud**
2. The shortcut appears in the Shortcuts app on your Watch
3. Tap it to add a task with voice input from your Watch

## Error Handling

- **401 Unauthorized**: Check that your API token is correct
- **422 Unprocessable Entity**: The task text was empty; provide text to add
- **Connection Error**: Verify your Taskboard server URL is accessible from your device

## Example JSON Response

On success (201 Created):
```json
{
  "id": 42,
  "title": "Buy groceries",
  "dueDate": "2026-07-01"
}
```
