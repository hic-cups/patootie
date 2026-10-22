package app.patootie.engine

/**
 * Where an app's private data lives on the device, and where Patootie keeps the
 * data of the clones that are not currently live.
 *
 * The store sits on the same /data partition as the app data, so saving and
 * restoring a clone is a rename, not a copy: swapping is instant no matter how
 * big the clone is. /data/adb is root-only and survives Patootie being
 * uninstalled, so clone data is never lost with it.
 */
data class DataLayout(
    val storeRoot: String = "/data/adb/patootie",
    /** Credential-encrypted data: /data/user/<u>/<pkg> (aka /data/data/<pkg>). */
    val ceRoot: String = "/data/user",
    /** Device-encrypted data: /data/user_de/<u>/<pkg>. */
    val deRoot: String = "/data/user_de",
    /** Lower filesystem of shared storage: /data/media/<u>/Android/data/<pkg>. */
    val mediaRoot: String = "/data/media",
)

/**
 * Generates the POSIX sh scripts Patootie runs as root. Every value spliced into
 * a script is validated first, so nothing user-controlled can reach the shell.
 *
 * On-disk store, per user and package:
 * ```
 * <storeRoot>/<u>/<pkg>/ACTIVE           id of the slot whose data is live
 * <storeRoot>/<u>/<pkg>/JOURNAL          "<from> <to> <phase>" while a swap runs
 * <storeRoot>/<u>/<pkg>/slots/<id>/      ce/ de/ ext/ uid ext.meta  (parked slots)
 * ```
 * A slot that has no directory is a fresh clone: activating it hands the app an
 * empty data dir, exactly like a new install.
 */
object SwapScripts {
    const val ORIGINAL = "original"

    /** Exit codes the scripts use; mirrored by [SwapError]. */
    const val EXIT_NO_APP_DATA = 11
    const val EXIT_SWAP_FAILED = 20
    const val EXIT_CORRUPT_JOURNAL = 22
    const val EXIT_SLOT_IS_LIVE = 30
    const val EXIT_SLOT_IN_FLIGHT = 31

    private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val SLOT_REGEX = Regex("^[a-z0-9][a-z0-9-]{0,39}$")
    private val PATH_REGEX = Regex("^/[A-Za-z0-9_./-]*$")

    fun isValidPackage(pkg: String) = pkg.length <= 255 && PACKAGE_REGEX.matches(pkg)
    fun isValidSlot(slot: String) = SLOT_REGEX.matches(slot)

    /** Switches [pkg]'s live data to [target], finishing any interrupted swap first. */
    fun activate(layout: DataLayout, userId: Int, pkg: String, appUid: Int, target: String): String =
        prelude(layout, userId, pkg, appUid) + """
            TARGET=${slot(target)}
            [ -d "${'$'}CE" ] || fail $EXIT_NO_APP_DATA "no data dir for ${'$'}PKG"
            am force-stop "${'$'}PKG" >/dev/null 2>&1
            resume_journal
            CUR=${'$'}(cat "${'$'}STORE/ACTIVE")
            if [ "${'$'}CUR" != "${'$'}TARGET" ]; then
              run_swap "${'$'}CUR" "${'$'}TARGET" save || fail $EXIT_SWAP_FAILED "swap ${'$'}CUR -> ${'$'}TARGET failed"
            fi
            echo "ACTIVE ${'$'}TARGET"
        """.trimIndent() + "\n"

    /** Makes sure the store exists for [pkg]; new clones need nothing else on disk. */
    fun prepare(layout: DataLayout, userId: Int, pkg: String, appUid: Int): String =
        prelude(layout, userId, pkg, appUid) + """
            [ -d "${'$'}CE" ] || fail $EXIT_NO_APP_DATA "no data dir for ${'$'}PKG"
            echo "ACTIVE ${'$'}(cat "${'$'}STORE/ACTIVE")"
        """.trimIndent() + "\n"

    /** Deletes a parked slot's data. Refuses while the slot is live or mid-swap. */
    fun deleteSlot(layout: DataLayout, userId: Int, pkg: String, appUid: Int, target: String): String {
        require(target != ORIGINAL) { "the original data can't be deleted" }
        return prelude(layout, userId, pkg, appUid) + """
            TARGET=${slot(target)}
            [ "${'$'}(cat "${'$'}STORE/ACTIVE")" = "${'$'}TARGET" ] && fail $EXIT_SLOT_IS_LIVE "slot is live"
            if [ -f "${'$'}J" ]; then
              read j_from j_to j_phase < "${'$'}J"
              { [ "${'$'}j_from" = "${'$'}TARGET" ] || [ "${'$'}j_to" = "${'$'}TARGET" ]; } && fail $EXIT_SLOT_IN_FLIGHT "slot is mid-swap"
            fi
            rm -rf "${'$'}SLOTS/${'$'}TARGET"
            if [ "${'$'}(cat "${'$'}STORE/ACTIVE")" = "$ORIGINAL" ] && [ ! -f "${'$'}J" ] && [ -z "${'$'}(ls -A "${'$'}SLOTS")" ]; then
              rm -rf "${'$'}STORE"
            fi
            echo "DELETED ${'$'}TARGET"
        """.trimIndent() + "\n"
    }

    /**
     * Re-fits all data to the app's current uid (after a reinstall gave it a new
     * one), recreates the cache dirs and relabels SELinux contexts.
     */
    fun repair(layout: DataLayout, userId: Int, pkg: String, appUid: Int): String =
        prelude(layout, userId, pkg, appUid) + """
            [ -d "${'$'}CE" ] || fail $EXIT_NO_APP_DATA "no data dir for ${'$'}PKG"
            am force-stop "${'$'}PKG" >/dev/null 2>&1
            resume_journal
            fix_owner
            ensure_cache
            for s in "${'$'}SLOTS"/*; do [ -d "${'$'}s" ] && echo "${'$'}APP_UID" > "${'$'}s/uid"; done
            echo "REPAIRED ${'$'}(cat "${'$'}STORE/ACTIVE")"
        """.trimIndent() + "\n"

    /**
     * One line per package that has clones: `STATUS <pkg> <active> <ok|pending>`,
     * then `SIZE <pkg> <slot> <kb>` for each parked slot.
     */
    fun status(layout: DataLayout, userId: Int): String {
        val root = path(layout.storeRoot)
        return """
            R=$root/$userId
            [ -d "${'$'}R" ] || exit 0
            for d in "${'$'}R"/*; do
              [ -d "${'$'}d" ] || continue
              p=${'$'}{d##*/}
              a=${'$'}(cat "${'$'}d/ACTIVE" 2>/dev/null)
              if [ -f "${'$'}d/JOURNAL" ]; then j=pending; else j=ok; fi
              echo "STATUS ${'$'}p ${'$'}{a:-$ORIGINAL} ${'$'}j"
              for s in "${'$'}d"/slots/*; do
                [ -d "${'$'}s" ] || continue
                echo "SIZE ${'$'}p ${'$'}{s##*/} ${'$'}(du -sk "${'$'}s" 2>/dev/null | cut -f1)"
              done
            done
        """.trimIndent() + "\n"
    }

    private fun prelude(layout: DataLayout, userId: Int, pkg: String, appUid: Int): String {
        require(isValidPackage(pkg)) { "bad package name: $pkg" }
        require(userId in 0..9999) { "bad user id: $userId" }
        require(appUid / 100_000 == userId && appUid % 100_000 >= 10_000) { "bad app uid: $appUid" }
        return """
            set -u
            PKG='$pkg'
            U=$userId
            APP_UID=$appUid
            STORE=${path(layout.storeRoot)}/${'$'}U/${'$'}PKG
            SLOTS="${'$'}STORE/slots"
            J="${'$'}STORE/JOURNAL"
            CE=${path(layout.ceRoot)}/${'$'}U/${'$'}PKG
            DE=${path(layout.deRoot)}/${'$'}U/${'$'}PKG
            EXT=${path(layout.mediaRoot)}/${'$'}U/Android/data/${'$'}PKG
            CACHE_GID=${'$'}(( U * 100000 + APP_UID % 100000 - 10000 + 20000 ))

            fail() { echo "ERR ${'$'}1 ${'$'}2" >&2; exit "${'$'}1"; }

            mkdir -p "${'$'}SLOTS" || exit 10
            [ -s "${'$'}STORE/ACTIVE" ] || echo $ORIGINAL > "${'$'}STORE/ACTIVE"

            # Moves everything inside dir $1 into dir $2 (renames, so it's instant).
            # Never overwrites: anything already at the destination is set aside.
            # The legacy /data/data/<pkg>/lib symlink belongs to the system; skip it.
            stash() {
              [ -d "${'$'}1" ] || return 0
              mkdir -p "${'$'}2" || return 1
              for f in "${'$'}1"/* "${'$'}1"/.[!.]* "${'$'}1"/..?*; do
                [ -e "${'$'}f" ] || [ -h "${'$'}f" ] || continue
                n=${'$'}{f##*/}
                [ "${'$'}n" = lib ] && [ -h "${'$'}f" ] && continue
                if [ -e "${'$'}2/${'$'}n" ] || [ -h "${'$'}2/${'$'}n" ]; then
                  mv -f "${'$'}2/${'$'}n" "${'$'}2/${'$'}n.patootie-bak-${'$'}${'$'}" || return 1
                fi
                mv -f "${'$'}f" "${'$'}2/" || return 1
              done
            }

            # Apps expect these two dirs to exist with the per-app cache group.
            ensure_cache() {
              for c in cache code_cache; do
                if [ ! -d "${'$'}CE/${'$'}c" ]; then
                  mkdir -p "${'$'}CE/${'$'}c"
                  chown "${'$'}APP_UID:${'$'}CACHE_GID" "${'$'}CE/${'$'}c"
                  chmod 2771 "${'$'}CE/${'$'}c"
                  restorecon "${'$'}CE/${'$'}c" >/dev/null 2>&1
                fi
              done
              return 0
            }

            fix_owner() {
              for d in "${'$'}CE" "${'$'}DE"; do
                [ -d "${'$'}d" ] && chown -hR "${'$'}APP_UID:${'$'}APP_UID" "${'$'}d"
              done
              for c in cache code_cache; do
                [ -d "${'$'}CE/${'$'}c" ] && chown -hR "${'$'}APP_UID:${'$'}CACHE_GID" "${'$'}CE/${'$'}c"
              done
              [ -d "${'$'}EXT" ] && chown -hR "${'$'}APP_UID" "${'$'}EXT"
              restorecon -R "${'$'}CE" "${'$'}DE" >/dev/null 2>&1
              [ -d "${'$'}EXT" ] && restorecon -R "${'$'}EXT" >/dev/null 2>&1
              return 0
            }

            # Shared storage dir went missing (the app or the system cleared it):
            # recreate it the way it was when this slot was parked.
            make_ext() {
              m_gid=${'$'}(stat -c %g "${'$'}{EXT%/*}" 2>/dev/null || echo ${'$'}APP_UID)
              m_mode=2770
              if [ -f "${'$'}1/ext.meta" ]; then
                read m_u m_gid m_mode < "${'$'}1/ext.meta"
              fi
              mkdir -p "${'$'}EXT" && chown "${'$'}APP_UID:${'$'}m_gid" "${'$'}EXT" && chmod "${'$'}m_mode" "${'$'}EXT"
              restorecon "${'$'}EXT" >/dev/null 2>&1
              return 0
            }

            # Phase "save" parks the live data in slot $1, phase "restore" brings
            # slot $2 in. The journal makes an interrupted swap resumable.
            run_swap() {
              sw_from=${'$'}1; sw_to=${'$'}2; sw_phase=${'$'}3
              if [ "${'$'}sw_phase" = save ]; then
                echo "${'$'}sw_from ${'$'}sw_to save" > "${'$'}J" || return 1
                sw_dst="${'$'}SLOTS/${'$'}sw_from"
                mkdir -p "${'$'}sw_dst" || return 1
                echo "${'$'}APP_UID" > "${'$'}sw_dst/uid"
                [ -d "${'$'}EXT" ] && stat -c '%u %g %a' "${'$'}EXT" > "${'$'}sw_dst/ext.meta"
                stash "${'$'}CE" "${'$'}sw_dst/ce" || return 1
                stash "${'$'}DE" "${'$'}sw_dst/de" || return 1
                stash "${'$'}EXT" "${'$'}sw_dst/ext" || return 1
              fi
              echo "${'$'}sw_from ${'$'}sw_to restore" > "${'$'}J" || return 1
              sw_src="${'$'}SLOTS/${'$'}sw_to"
              sw_uid=${'$'}APP_UID
              if [ -d "${'$'}sw_src" ]; then
                if [ -d "${'$'}sw_src/ext" ] && [ ! -d "${'$'}EXT" ]; then make_ext "${'$'}sw_src"; fi
                stash "${'$'}sw_src/ce" "${'$'}CE" || return 1
                [ -d "${'$'}DE" ] && { stash "${'$'}sw_src/de" "${'$'}DE" || return 1; }
                [ -d "${'$'}EXT" ] && { stash "${'$'}sw_src/ext" "${'$'}EXT" || return 1; }
                [ -f "${'$'}sw_src/uid" ] && sw_uid=${'$'}(cat "${'$'}sw_src/uid")
              fi
              ensure_cache
              [ "${'$'}sw_uid" = "${'$'}APP_UID" ] || fix_owner
              echo "${'$'}sw_to" > "${'$'}STORE/ACTIVE" || return 1
              # Only throw the parked copy away once nothing is left in it.
              if [ -d "${'$'}sw_src" ]; then
                for d in ce de ext; do
                  if [ -d "${'$'}sw_src/${'$'}d" ] && [ -n "${'$'}(ls -A "${'$'}sw_src/${'$'}d")" ]; then
                    mv "${'$'}sw_src" "${'$'}SLOTS/${'$'}sw_to.leftover-${'$'}${'$'}" || return 1
                    break
                  fi
                done
                rm -rf "${'$'}sw_src"
              fi
              rm -f "${'$'}J"
            }

            resume_journal() {
              [ -f "${'$'}J" ] || return 0
              j_from=; j_to=; j_phase=
              read j_from j_to j_phase < "${'$'}J"
              case "${'$'}j_phase" in save|restore) ;; *) fail $EXIT_CORRUPT_JOURNAL "corrupt journal" ;; esac
              [ -n "${'$'}j_from" ] && [ -n "${'$'}j_to" ] || fail $EXIT_CORRUPT_JOURNAL "corrupt journal"
              run_swap "${'$'}j_from" "${'$'}j_to" "${'$'}j_phase" || fail $EXIT_SWAP_FAILED "resume failed"
            }

        """.trimIndent() + "\n"
    }

    private fun slot(id: String): String {
        require(isValidSlot(id)) { "bad slot id: $id" }
        return "'$id'"
    }

    private fun path(p: String): String {
        require(PATH_REGEX.matches(p) && ".." !in p) { "bad path: $p" }
        return "'$p'"
    }
}
