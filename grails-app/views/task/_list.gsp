<g:each in="${tasks}" var="task">
    <g:render template="card"
        model="[task: task, color: urgencyService.colorFor(task, today)]"/>
</g:each>
