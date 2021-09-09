# Aggregate Service

The purpose of this service is to retrieve block data overtime from the blockchain so that we can compute aggregated data at a reasonable rate to perform business decision queries.

## General Setup
You will need the AWS CLI packaged installed, found at: https://aws.amazon.com/cli/

Once setup, run `aws configure` to begin token and region configurations.

If running Localstack (see below), then set the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` to `test`, otherwise you can setup a specific profile `aws configure --profile <profile_name>` if you are permissioned to an AWS instance.

We utilize serverless for our AWS infrastructure deployment via github actions: https://serverless.com/. Install the serverless package to test and develop with new configurations. `npm install -g serverless` 

## Local Setup

When developing locally, we utilize the tool Localstack that runs a mock of the AWS services instead of developing off a real AWS instance.

Run the below setup to use Localstack:
- Install python: `brew install python`
- Install localstack using python's pip installer: `pip install localstack`
The current project detects that you are running locally and will spin a docker container instance of localstack but in the event you would like to manually start your own AWS service locally you could run:
  
`docker run --rm -it -p 4566:4566 -p 4571:4571 localstack/localstack`

Alternatively, you can run localstack with selected services, example:

`SERVICES=s3,cloudformation,sts DEBUG=1 localstack start`

