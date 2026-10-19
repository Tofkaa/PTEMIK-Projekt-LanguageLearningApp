import ChoiceOptions from './ChoiceOptions';

const MultipleChoiceExercise = ({ data, currentAnswer, onAnswer, disabled }) => (
    <ChoiceOptions options={data?.options} currentAnswer={currentAnswer}
        onAnswer={onAnswer} disabled={disabled} lang={data?.answerLang} />
);

export default MultipleChoiceExercise;
