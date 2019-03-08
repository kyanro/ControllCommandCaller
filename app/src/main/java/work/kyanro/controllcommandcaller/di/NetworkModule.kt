package work.kyanro.controllcommandcaller.di

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import work.kyanro.controllcommandcaller.network.CccApiService

class NetworkModule {
    fun providesBaseUrl() = "http://192.168.0.1"

    fun providesOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    fun providesCccService(baseUrl: String, okHttpClient: OkHttpClient): CccApiService {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory()
    }
}