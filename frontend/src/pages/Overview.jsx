import StorageCategoryCard from "../components/StorageCategoryCard.jsx";
import StorageCard from "../components/UI/StorageCard.jsx";
import calculateStorage from "../components/Utility/calculateStorage.jsx";

const Overview = (props) => {
  const result = calculateStorage(props.data);
  console.log(result.used);

  return (
    <div className="w-[40%] h-[100%] bg-purple-200 flex flex-col justify-around items-center rounded-2xl px-1">
      <StorageCard used={result.used} />
      <div className="flex gap-6 items-center justify-center flex-wrap">
        <StorageCategoryCard type="document" storage={result.document} title="Document" lastUpdate="10:15am, 10 Oct" />
        <StorageCategoryCard type="image" storage={result.image} title="Images" lastUpdate="10:45am, 18 Sep"  />
        <StorageCategoryCard type="media" storage={result.media} title="Videos" lastUpdate="10:15am, 10 Oct"  />
        <StorageCategoryCard type="other" storage={result.other} title="Others" lastUpdate="10:15am, 10 Oct"  />
      </div>
    </div>
  );
};

export default Overview;
