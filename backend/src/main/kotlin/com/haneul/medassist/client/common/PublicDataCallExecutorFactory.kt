package com.haneul.medassist.client.common

import com.haneul.medassist.config.PublicDataClientPolicy

class PublicDataCallExecutorFactory {
    fun create(policy: PublicDataClientPolicy): PublicDataCallExecutor = PublicDataCallExecutor(policy)
}
