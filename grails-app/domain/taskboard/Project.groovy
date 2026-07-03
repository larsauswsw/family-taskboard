package taskboard

/**
 * A label for grouping tasks. Deliberately has no GORM hasMany/belongsTo
 * relationship with Task -- see ProjectService.delete() for why: a
 * bidirectional belongsTo/hasMany pair would make GORM cascade-delete every
 * task referencing a project when that project is deleted, which is wrong
 * here (a project is just a grouping label, not an owner of its tasks).
 */
class Project {
    String name
    String color

    static constraints = {
        name blank: false, nullable: false
        color blank: false, nullable: false, matches: /^#[0-9A-Fa-f]{6}$/
    }
}
