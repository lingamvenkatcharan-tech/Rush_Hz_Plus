package com.example.rush_hz_plus.core.utils


/**
 * 한 번만 소비되어야 하는 데이터를 감싸는 Wrapper 클래스
 */
open class Event<out T>(private val content: T) {

    private var hasBeenHandled = false

    /**
     * 이벤트가 아직 처리되지 않았다면 값을 반환하고,
     * 이후로는 null을 반환 (즉, 한 번만 사용됨)
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) null
        else {
            hasBeenHandled = true
            content
        }
    }

    /** 이미 처리되었더라도 값을 읽기 위해 사용 */
    fun peekContent(): T = content
}
