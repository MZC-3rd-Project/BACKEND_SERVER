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

{{- define "spring-service.strategyType" -}}
{{- default "rolling" .Values.deploymentStrategy.type -}}
{{- end -}}

{{- define "spring-service.activeServiceName" -}}
{{- include "spring-service.fullname" . -}}
{{- end -}}

{{- define "spring-service.previewServiceName" -}}
{{- printf "%s-preview" (include "spring-service.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "spring-service.workloadApiVersion" -}}
{{- if eq (include "spring-service.strategyType" .) "blueGreen" -}}
argoproj.io/v1alpha1
{{- else -}}
apps/v1
{{- end -}}
{{- end -}}

{{- define "spring-service.workloadKind" -}}
{{- if eq (include "spring-service.strategyType" .) "blueGreen" -}}
Rollout
{{- else -}}
Deployment
{{- end -}}
{{- end -}}

{{- define "spring-service.podTemplate" -}}
template:
  metadata:
    labels:
      {{- include "spring-service.selectorLabels" . | nindent 6 }}
      {{- with .Values.podLabels }}
      {{- toYaml . | nindent 6 }}
      {{- end }}
    {{- with .Values.podAnnotations }}
    annotations:
      {{- toYaml . | nindent 6 }}
    {{- end }}
  spec:
    serviceAccountName: {{ include "spring-service.serviceAccountName" . }}
    {{- with .Values.podSecurityContext }}
    securityContext:
      {{- toYaml . | nindent 6 }}
    {{- end }}
    containers:
      - name: {{ include "spring-service.name" . }}
        image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
        imagePullPolicy: {{ .Values.image.pullPolicy }}
        {{- with .Values.container.command }}
        command:
          {{- toYaml . | nindent 10 }}
        {{- end }}
        {{- with .Values.container.args }}
        args:
          {{- toYaml . | nindent 10 }}
        {{- end }}
        ports:
          - name: http
            containerPort: {{ .Values.container.port }}
            protocol: TCP
          {{- with .Values.container.extraPorts }}
          {{- toYaml . | nindent 10 }}
          {{- end }}
        {{- if or .Values.env (and .Values.externalSecret.enabled .Values.secretEnv) }}
        envFrom:
          {{- if .Values.env }}
          - configMapRef:
              name: {{ include "spring-service.configMapName" . }}
          {{- end }}
          {{- if and .Values.externalSecret.enabled .Values.secretEnv }}
          - secretRef:
              name: {{ include "spring-service.secretName" . }}
          {{- end }}
        {{- end }}
        {{- with .Values.extraEnv }}
        env:
          {{- toYaml . | nindent 10 }}
        {{- end }}
        {{- if .Values.container.extraVolumeMounts }}
        volumeMounts:
          {{- toYaml .Values.container.extraVolumeMounts | nindent 10 }}
        {{- end }}
        {{- with .Values.securityContext }}
        securityContext:
          {{- toYaml . | nindent 10 }}
        {{- end }}
        {{- with .Values.resources }}
        resources:
          {{- toYaml . | nindent 10 }}
        {{- end }}
        {{- if .Values.probes.liveness.enabled }}
        livenessProbe:
          httpGet:
            path: {{ .Values.probes.liveness.path }}
            port: {{ .Values.probes.liveness.port }}
          initialDelaySeconds: {{ .Values.probes.liveness.initialDelaySeconds }}
          periodSeconds: {{ .Values.probes.liveness.periodSeconds }}
          timeoutSeconds: {{ .Values.probes.liveness.timeoutSeconds }}
          failureThreshold: {{ .Values.probes.liveness.failureThreshold }}
        {{- end }}
        {{- if .Values.probes.readiness.enabled }}
        readinessProbe:
          httpGet:
            path: {{ .Values.probes.readiness.path }}
            port: {{ .Values.probes.readiness.port }}
          initialDelaySeconds: {{ .Values.probes.readiness.initialDelaySeconds }}
          periodSeconds: {{ .Values.probes.readiness.periodSeconds }}
          timeoutSeconds: {{ .Values.probes.readiness.timeoutSeconds }}
          failureThreshold: {{ .Values.probes.readiness.failureThreshold }}
        {{- end }}
    {{- with .Values.nodeSelector }}
    nodeSelector:
      {{- toYaml . | nindent 6 }}
    {{- end }}
    {{- with .Values.affinity }}
    affinity:
      {{- toYaml . | nindent 6 }}
    {{- end }}
    {{- with .Values.tolerations }}
    tolerations:
      {{- toYaml . | nindent 6 }}
    {{- end }}
    {{- if or .Values.fileConfigMaps .Values.extraVolumes }}
    volumes:
      {{- range $cfg := .Values.fileConfigMaps }}
      - name: {{ include "spring-service.fileConfigMapVolumeName" $cfg.name }}
        configMap:
          name: {{ include "spring-service.fileConfigMapName" (dict "root" $ "name" $cfg.name) }}
      {{- end }}
      {{- with .Values.extraVolumes }}
      {{- toYaml . | nindent 6 }}
      {{- end }}
    {{- end }}
{{- end -}}
