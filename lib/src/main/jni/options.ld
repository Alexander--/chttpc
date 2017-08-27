SECTIONS {
  /DISCARD/ : {
    *(.note.gnu.gold-version)
    *(.comment)
  }
}
INSERT BEFORE .text;