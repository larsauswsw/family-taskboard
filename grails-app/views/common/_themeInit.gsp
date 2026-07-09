<script>
(function () {
    const stored = localStorage.getItem('taskboard-theme');
    if (stored) {
        document.documentElement.setAttribute('data-theme', stored);
    }
})();
</script>
