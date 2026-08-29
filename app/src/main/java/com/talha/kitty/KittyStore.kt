package com.talha.kitty

import android.content.Context

/**
 * Application-scoped holder for all kitty records.
 * Loads once from disk, keeps the in-memory list, and persists on every change.
 */
object KittyStore {
    private var repository: KittyRepository? = null
    var kitties: MutableList<Kitty> = ArrayList()
        private set

    fun init(context: Context) {
        if (repository == null) {
            repository = KittyRepository(context.applicationContext)
            kitties = repository!!.load()
        }
    }

    fun persist() {
        repository?.save(kitties)
    }

    fun add(kitty: Kitty) {
        kitties.add(kitty)
        persist()
    }

    fun update() {
        persist()
    }

    fun remove(kitty: Kitty) {
        kitties.remove(kitty)
        persist()
    }
}
