<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Projekte</title>
    <asset:stylesheet src="taskboard.css"/>
    <g:render template="/common/themeInit"/>
</head>
<body>
    <g:render template="/common/navbar" model="[title: 'Projekte', linkHome: true]"/>

    <main class="settings-page">
        <g:render template="manage" model="[projects: projects, error: error]"/>
    </main>
</body>
</html>
