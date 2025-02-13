/*
 * Copyright 2023 Google LLC
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

package com.google.android.ground.persistence.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.ground.repository.SurveyRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/** Worker responsible for syncing latest surveys and LOIs from remote server to local db. */
@HiltWorker
class SurveySyncWorker
@AssistedInject
constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val surveyRepository: SurveyRepository
) : Worker(context, params) {
  private val surveyId: String? = params.inputData.getString(SURVEY_ID_PARAM_KEY)

  override fun doWork(): Result {
    if (surveyId == null) {
      Timber.e("Survey sync scheduled with null surveyId")
      return Result.failure()
    }

    // It's ok to block here since WorkManager calls doWork() on a background thread.
    Timber.d("Syncing survey $surveyId")
    surveyRepository.syncSurveyWithRemote(surveyId).ignoreElement().blockingAwait()

    // TODO(https://github.com/google/ground-android/issues/1383): Also sync remote LOIs to localdb.
    // TODO: Handle failures - log and retry.
    return Result.success()
  }

  companion object {
    /** The key in worker input data containing the id of the survey to be synced. */
    private const val SURVEY_ID_PARAM_KEY = "surveyId"

    /** Returns a new work [Data] object containing the specified survey id. */
    fun createInputData(surveyId: String): Data =
      Data.Builder().putString(SURVEY_ID_PARAM_KEY, surveyId).build()
  }
}
