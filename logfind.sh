#!/bin/bash

name=$1
find=${2:-""}
namespace=$3
dump=0

# Display help text if no arguments are provided or if -h/--help is passed
if [[ -z "$1" || "$1" == "-h" || "$1" == "--help" ]]; then
  echo "Usage: $0 <app-name> [search-term] <namespace> [--dump]"
  echo "Assumes you have setup kubectl and set its context to desired environment (i.e dev-gcp)"
  echo "Arguments:"
  echo "  <app-name>  	    The name of the application whose pods are searched for files"
  echo "  [search-term]     The term to search for in files within the /tmp, directory. Use \"\" or omit to show all files"
  echo "  [namespace]       The Kubernetes namespace where the pods are located. If context is preconfigured this can be omitted (i.e kubectl config set-context <dev/prod>-gcp --namespace=hot-crm)"
  echo "  [--dump]          Optional. If provided, dumps the content of each found file."
  echo "Examples:"
  echo "  $0 sf-linkmobility \"\" hot-crm			Shows all files for app sf-linkmobility in current context"	
  echo "  $0 sf-linkmobility					Same as above, requires context has been configured to namespace (i.e kubectl config set-context <dev/prod>-gcp --namespace=hot-crm)"
  echo "  $0 sf-linkmobility accessTokenReq hot-crm		Matches files found with pattern accessTokenReq (hot-crm can be omitted if preconfigured)"
  echo "  $0 sf-linkmobility latestForward hot-crm --dump	Matches files found with pattern latestForward and dumps the content of the files (hot-crm can be omitted if preconfigured)"
  echo "Description:"
  echo "  This script searches for files in the /tmp directory of Kubernetes pods for the specified application and namespace."
  echo "  If a search term is provided, only files containing the term in their names are listed."
  echo "  Use the --dump flag to display the contents of the matched files."
  exit 0
fi

# Check if the --dump flag is present in the arguments
if [[ "$2" == "--dump" || "$3" == "--dump" || "$4" == "--dump" ]]; then
  dump=1
fi
# Ensure that `find` and `namespace` are correctly set if --dump is passed as $2 or $3
if [[ "$2" == "--dump" ]]; then
  find=""
  namespace=$3
elif [[ "$3" == "--dump" ]]; then
  namespace=$4
fi

# Check if namespace is set or empty
if [ -z "$namespace" ]; then
  # Fetch namespace from current context if not set
  namespace=$(kubectl config view --minify --output 'jsonpath={..namespace}')
  
  # If still empty, set to 'default' as the fallback
  if [ -z "$namespace" ]; then
    namespace="not set"
  fi
fi

currentcontext=$(kubectl config current-context)
echo "Context: $currentcontext"
echo "File match: ${find:-<All>}"
echo "Namespace: ${namespace}"

# Get all pod names matching the specified name
podnames=$(kubectl get pods --namespace=$namespace --no-headers -o custom-columns=":metadata.name" | grep $name)

echo $podnames

# Count the number of matching pods
count=$(echo "$podnames" | wc -l)

# Iterate over each pod and perform the search
i=1
for podname in $podnames; do
  echo "Checking replica $i/$count - $podname"
  files=$(kubectl exec -it $podname -c $name --namespace=$namespace -- find /tmp -type f -name "*$find*")
  
  if [ -n "$files" ]; then

    files=$(echo "$files" | tr -d '\r')

    echo "$files"

    # If the --dump flag is set, display the content of each found file
    if [ $dump -eq 1 ]; then
      for file in $files; do
        echo "---- Content of $file ----"
        kubectl exec -it $podname -c $name --namespace=$namespace -- cat "$file"
	echo
        echo "----------------------------"
      done
    fi
  fi
  i=$((i+1))
done