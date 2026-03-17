{{- define "spring-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "spring-service.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "spring-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "spring-service.labels" -}}
helm.sh/chart: {{ include "spring-service.chart" . }}
app.kubernetes.io/name: {{ include "spring-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "spring-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "spring-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "spring-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "spring-service.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "spring-service.configMapName" -}}
{{- printf "%s-config" (include "spring-service.fullname" .) -}}
{{- end -}}

{{- define "spring-service.secretName" -}}
{{- printf "%s-secret" (include "spring-service.fullname" .) -}}
{{- end -}}

{{- define "spring-service.fileConfigMapName" -}}
{{- $root := .root -}}
{{- $name := .name -}}
{{- printf "%s-%s" (include "spring-service.fullname" $root) $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "spring-service.fileConfigMapVolumeName" -}}
{{- printf "file-cm-%s" . | trunc 63 | trimSuffix "-" -}}
{{- end -}}
