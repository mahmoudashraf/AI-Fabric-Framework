{{- define "ai-fabric-relay.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "ai-fabric-relay.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := include "ai-fabric-relay.name" . -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "ai-fabric-relay.labels" -}}
app.kubernetes.io/name: {{ include "ai-fabric-relay.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- end -}}

{{- define "ai-fabric-relay.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ai-fabric-relay.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
