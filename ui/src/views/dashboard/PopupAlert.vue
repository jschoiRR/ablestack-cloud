// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

<template>
  <a-spin :spinning="loading" v-ctrl-enter="handleSubmit">
    <a-button
        v-if="this.selectedRowKeys.length > 0"
        type="primary"
        style="width: 100%; margin-bottom: 15px"
        @click="handleSubmit()">
        {{ $t('label.alert.show') }}
      </a-button>
    <a-table
      size="middle"
      style="overflow-y: auto"
      :columns="columns"
      :dataSource="alerts"
      :pagination="false"
      :rowSelection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange}"
      :scroll="{ y: '60vh', x: '50vw' }"
      :rowKey="item => item.id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'sent'">
          <span :style="{ color: '#999' }"><small>{{ $toLocaleDate(record.sent) }}</small></span>
        </template>
        <template v-if="column.key === 'name'">
          <span :style="{ color: '#666' }"><small><router-link :to="{ path: '/alert/' + record.id }">{{ record.name }}</router-link></small></span>
        </template>
        <template v-if="column.key === 'description'">
          <span :style="{ color: '#aaa' }">{{ record.description }}</span>
        </template>
      </template>
    </a-table>
  </a-spin>
</template>

<script>
import { api } from '@/api'
import ResourceIcon from '@/components/view/ResourceIcon'

export default {
  name: 'PopupAlert',
  components: {
    ResourceIcon
  },
  data () {
    return {
      loading: false,
      alerts: [],
      popupAlertModal: false,
      alertCheck: true,
      columns: [],
      selectedRowKeys: [],
      selectedItems: []
    }
  },
  created () {
    this.columns = [
      {
        key: 'name',
        title: this.$t('label.name'),
        dataIndex: 'name',
        width: '15%'
      },
      {
        key: 'sent',
        title: this.$t('label.date'),
        dataIndex: 'sent',
        width: '15%'
      },
      {
        key: 'description',
        title: this.$t('label.description'),
        dataIndex: 'description'
      }
    ]
    this.fetchData()
  },
  watch: {
    active () {
      this.popupAlertModal = this.active
    }
  },
  mounted () {
    this.popupAlertModal = this.active
  },
  methods: {
    fetchData () {
      const params = {
        listall: true
      }
      this.loading = true
      api('listAlerts', params).then(json => {
        this.alerts = []
        this.loading = false
        if (json && json.listalertsresponse && json.listalertsresponse.alert) {
          const alertsList = json.listalertsresponse.alert || []
          for (var i = 0; i < alertsList.length; i++) {
            if (alertsList[i].showalert === true) {
              this.alerts.push(alertsList[i])
            }
          }
        }
      })
    },
    onSelectChange (selectedRowKeys, selectedRows) {
      this.setSelection(selectedRowKeys)
    },
    setSelection (selection) {
      this.selectedRowKeys = selection
      this.$emit('selection-change', this.selectedRowKeys)
      this.selectedItems = (this.alerts.filter(function (item) {
        return selection.indexOf(item.id) !== -1
      }))
    },
    handleSubmit (e) {
      for (const alert of this.selectedItems) {
        this.updateAlert(alert)
      }
    },
    updateAlert (alert) {
      if (this.loading) return
      api('updateAlert', {
        id: alert.id,
        showAlert: false
      }).then(response => {
        this.$emit('close-action')
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.$store.commit('SET_SHOW_ALERT', false)
        this.loading = false
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.form {
  width: 80vw;

  @media (min-width: 500px) {
    min-width: 400px;
    width: 100%;
  }
}
</style>
