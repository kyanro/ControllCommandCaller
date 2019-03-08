package work.kyanro.controllcommandcaller.network

import kotlinx.coroutines.Deferred
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

enum class Button(val id: Int) {
    A(8), B(9), Start(4), Select(5);

    override fun toString() = id.toString()
}

enum class Dpad(val id: Int) {
    // @formatter:off
    LeftUp  (7), Up  (8), RightUp  (9),
    Left    (4), None(5), Right    (6),
    LeftDown(1), Down(2), RightDown(3);
    // @formatter:on

    override fun toString() = id.toString()
}

/**
 * G.C.+Node-RED初期設定で利用可能なPath
 * /btn/push/{id}
 * /btn/hold/{id}
 * /btn/release/{id}
 * /btn/{id} ※push のシンタックスシュガー
 * /dpad/push/{id}
 * /dpad/hold/{id}
 * /dpad/{id} ※pushのシンタックスシュガー
 * /stk0/{x,y}/{value} ※valueは -127 ～127
 * /stk1/{x,y}/{value}
 * /input_config/{0,1} ※DP
 */
interface CccApiService {
    @GET("/btn/hold/{id}")
    fun hold(@Path("id") button: Button): Deferred<Unit>

    @GET("/btn/release/{id}")
    fun release(@Path("id") button: Button): Deferred<Unit>

    @GET("/dpad/hold/{id}")
    fun hold(@Path("id") dpad: Dpad): Deferred<Unit>

    @GET("/btn/release/{id}")
    fun release(@Path("id") dpad: Dpad): Deferred<Unit>
}