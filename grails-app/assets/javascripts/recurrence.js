function taskboardToggleRecurrenceFields(selectEl) {
    const form = selectEl.closest('form');
    const isWeekdays = selectEl.value === 'WEEKDAYS';
    form.querySelector('.recurrence-interval-field').hidden = isWeekdays;
    form.querySelector('.recurrence-weekday-fields').hidden = !isWeekdays;
}
