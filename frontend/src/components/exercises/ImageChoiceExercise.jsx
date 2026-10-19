import ChoiceOptions from './ChoiceOptions';
import ExerciseImage from '../ExerciseImage';

const ImageChoiceExercise = ({ exercise, currentAnswer, onAnswer, disabled }) => {
    const { content } = exercise;
    return <div>
        <ExerciseImage exercise={exercise} />
        <ChoiceOptions options={content?.options} currentAnswer={currentAnswer}
            onAnswer={onAnswer} disabled={disabled} grid lang={content?.answerLang} />
    </div>;
};

export default ImageChoiceExercise;
