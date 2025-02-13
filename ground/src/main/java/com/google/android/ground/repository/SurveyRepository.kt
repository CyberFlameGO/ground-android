/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.ground.repository

import com.google.android.ground.coroutines.IoDispatcher
import com.google.android.ground.model.Survey
import com.google.android.ground.model.User
import com.google.android.ground.persistence.local.LocalValueStore
import com.google.android.ground.persistence.local.stores.LocalSurveyStore
import com.google.android.ground.persistence.remote.NotFoundException
import com.google.android.ground.persistence.remote.RemoteDataStore
import com.google.android.ground.rx.annotations.Cold
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import java.util.concurrent.TimeUnit
import java8.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.awaitSingleOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val LOAD_REMOTE_SURVEY_TIMEOUT_SECS: Long = 15
private const val LOAD_REMOTE_SURVEY_SUMMARIES_TIMEOUT_SECS: Long = 30

/**
 * Coordinates persistence and retrieval of [Survey] instances from remote, local, and in memory
 * data stores. For more details on this pattern and overall architecture, see
 * https://developer.android.com/jetpack/docs/guide.
 */
@Singleton
class SurveyRepository
@Inject
constructor(
  private val localSurveyStore: LocalSurveyStore,
  private val remoteDataStore: RemoteDataStore,
  private val localValueStore: LocalValueStore,
  @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

  /**
   * Emits the currently active survey on subscribe and on change. Emits `empty()`when no survey is
   * active or local db isn't up-to-date.
   */
  val activeSurvey: @Cold Flowable<Optional<Survey>> =
    localValueStore.activeSurveyIdFlowable.distinctUntilChanged().switchMapMaybe {
      if (it.isEmpty()) Maybe.just(Optional.empty())
      else localSurveyStore.getSurveyById(it).map { s -> Optional.of(s) }
    }

  var activeSurveyId: String by localValueStore::activeSurveyId

  val offlineSurveys: @Cold Flowable<List<Survey>>
    get() = localSurveyStore.surveys

  private suspend fun syncSurveyFromRemote(surveyId: String): Survey {
    val survey = syncSurveyWithRemote(surveyId).await()
    remoteDataStore.subscribeToSurveyUpdates(surveyId).await()
    return survey
  }

  /** This only works if the survey is already cached to local db. */
  fun getOfflineSurvey(surveyId: String): @Cold Single<Survey> =
    localSurveyStore
      .getSurveyById(surveyId)
      .switchIfEmpty(Single.error { NotFoundException("Survey not found $surveyId") })

  fun syncSurveyWithRemote(id: String): @Cold Single<Survey> =
    remoteDataStore
      .loadSurvey(id)
      .timeout(LOAD_REMOTE_SURVEY_TIMEOUT_SECS, TimeUnit.SECONDS)
      .flatMap { localSurveyStore.insertOrUpdateSurvey(it).toSingleDefault(it) }
      .doOnSubscribe { Timber.d("Loading survey $id") }
      .doOnError { err -> Timber.d(err, "Error loading survey from remote") }

  suspend fun activateSurvey(surveyId: String) {
    // Do nothing if survey is already active.
    if (surveyId == activeSurveyId) {
      return
    }
    // Clear survey if id is empty.
    if (surveyId.isEmpty()) {
      clearActiveSurvey()
      return
    }

    withContext(ioDispatcher) {
      localSurveyStore.getSurveyById(surveyId).awaitSingleOrNull() ?: syncSurveyFromRemote(surveyId)
      activeSurveyId = surveyId
    }
  }

  fun clearActiveSurvey() {
    activeSurveyId = ""
  }

  fun getSurveySummaries(user: User): @Cold Single<List<Survey>> =
    loadSurveySummariesFromRemote(user)
      .doOnSubscribe { Timber.d("Loading survey list from remote") }
      .doOnError { Timber.d(it, "Failed to load survey list from remote") }
      .onErrorResumeNext { offlineSurveys.single(listOf()) }

  private fun loadSurveySummariesFromRemote(user: User): @Cold Single<List<Survey>> =
    remoteDataStore
      .loadSurveySummaries(user)
      .timeout(LOAD_REMOTE_SURVEY_SUMMARIES_TIMEOUT_SECS, TimeUnit.SECONDS)

  /** Attempts to remove the locally synced survey. Doesn't throw an error if it doesn't exist. */
  suspend fun removeOfflineSurvey(surveyId: String) {
    val survey = localSurveyStore.getSurveyById(surveyId).awaitSingleOrNull()
    survey?.let { localSurveyStore.deleteSurvey(survey).await() }
    if (activeSurveyId == surveyId) {
      clearActiveSurvey()
    }
  }
}
