# Apple Shortcut for Quick Task Entry

This guide shows how to set up Apple Shortcuts to quickly add tasks to Taskboard using the REST API endpoint.

## Overview

The Apple Shortcut automates the following workflow:
1. **Dictate Text** (German Shortcuts app: *Text diktieren*): Capture a voice input or text
2. **POST to /api/tasks/quick** (*Inhalte von URL abrufen*): Send the text to Taskboard with Bearer token authentication
3. **Show Result** (*Ergebnis anzeigen*): Display the created task confirmation

## Setup Steps

### 1. Get Your API Token

Log in to Taskboard and open **⚙️ Einstellungen** (top of the task list) --
your API token is shown there, ready to copy. If you ever need to invalidate
it (e.g. it leaked, or you just want a fresh one), click **"Token neu
generieren"**; any Shortcut still using the old value will start getting 401
errors until you paste in the new one.

### 2. Create the Shortcut

1. Open the **Shortcuts** app (*Kurzbefehle*) on your Mac or iOS device
2. Create a New Shortcut
3. Add these actions in order:

#### Action 1: Dictate Text (*Text diktieren*)
- **Type**: "Dictate Text" (*Text diktieren*)
- **Settings** (*Einstellungen*): Leave defaults (captures voice or allows text entry)

#### Action 2: Get Contents of URL (*Inhalte von URL abrufen*)
- **Type**: "Get Contents of URL" (*Inhalte von URL abrufen*)
- **URL**: `https://your-domain.com/api/tasks/quick` (replace `your-domain.com` with your Taskboard server)
- **Method** (*Methode*): `POST`
- **Headers** (*Kopfzeilen*):
  - Key (*Schlüssel*): `Authorization`
  - Value (*Wert*): `Bearer YOUR_API_TOKEN` (paste your API token from step 1)
- **Request Body** (*Anfrage*):
  - Type (*Anfragetext*): `Form Data` (*Formular*) or `JSON`
  - JSON Body:
    ```json
    {
      "text": "[Dictated Text output]"
    }
    ```

#### Action 3: Show Result (*Ergebnis anzeigen*)
- **Type**: "Show Result" (*Ergebnis anzeigen*)
- **Input** (*Eingabe*): [Response from previous step]
- Displays the created task details (ID, title, due date)

## Assign to Siri, Back Tap, or Action Button

### Via Siri Phrase
1. In Shortcuts (*Kurzbefehle*), find your shortcut
2. Tap the `•••` menu
3. **Add to Siri** (*Zu Siri hinzufügen*) → Set a voice phrase like "Add task to Taskboard"
4. Confirm and tap **Done** (*Fertig*)
5. Now say "Hey Siri, Add task to Taskboard" to use it

### Via Back Tap (iPhone/iPad) — *Auf Rückseite tippen*
1. Go to **Settings** (*Einstellungen*) → **Accessibility** (*Bedienungshilfen*) → **Touch** (*Tippen*)
2. Select **Back Tap** (*Auf Rückseite tippen*)
3. Choose a tap pattern (double or triple tap) (*Doppeltippen*/*Dreifach tippen*)
4. Assign your Taskboard shortcut
5. Double or triple tap the back of your device to trigger the shortcut

### Via Action Button (iPhone 15 Pro) — *Action-Taste*
1. Go to **Settings** (*Einstellungen*) → **Action Button** (*Action-Taste*)
2. Select your shortcut
3. Press and hold the Action Button to activate

### Via Lock Screen Widget — *Sperrbildschirm-Widget*
1. Long-press your Lock Screen (*Sperrbildschirm*)
2. Tap **Customize** (*Anpassen*)
3. Add a **Shortcuts** (*Kurzbefehle*) widget
4. Configure it to show your Taskboard shortcut
5. Tap the widget on Lock Screen to run it

## Sync via iCloud

All shortcuts sync automatically to your Apple Watch via iCloud if enabled:
1. Ensure **iCloud Drive** is enabled in **Settings** (*Einstellungen*) → **[Your Name]** (*[Dein Name]*) → **iCloud**
2. The shortcut appears in the Shortcuts app (*Kurzbefehle*-App) on your Watch
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
